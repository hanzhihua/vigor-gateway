package com.unknown.vigor.gateway.core.source;


import com.unknown.vigor.gateway.common.constant.GeneralConstant;
import com.unknown.vigor.gateway.common.protobuf.StreamEvent;
import com.unknown.vigor.gateway.core.LifecycleState;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class HttpSource extends AbstractSource {
    private List<EventLoopGroup> bossGroupList = new ArrayList<>();
    private EventLoopGroup workerGroup;
    private List<Channel> channelList = new ArrayList<>();

    private final static String HEALTH_CHECK_PATH = "/health/check";
    private List<String> whiteListPath;

    /**
     * 用于优雅停止
     */
    private final AtomicLong requestAfterStop = new AtomicLong();

    @Override
    public void start() throws Exception {
        whiteListPath = conf.getHttpWhiteList(getId().getTag());

        if (conf.getSourceUseNettyEPoll(getId().getTag())) {
            startEpollServer();
        } else {
            startNioServer();
        }

        super.start();
    }

    private void startNioServer() throws InterruptedException {
        log.info("use netty nio model");

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        bossGroupList.add(bossGroup);
        workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(NioServerSocketChannel.class);
        serverBootstrap.group(bossGroup, workerGroup);

        setCommonNettyParams(serverBootstrap);
    }

    private void startEpollServer() throws InterruptedException {
        log.info("use netty ePoll model");

        int workGroupMultiple = conf.getNettyWorkGroupMultiple(getId().getTag());

        for (int index = 0; index < conf.getNettyBossGroupSize(getId().getTag()); index++) {
            EventLoopGroup bossGroup = new EpollEventLoopGroup();
            bossGroupList.add(bossGroup);
            if (workerGroup == null) {
                workerGroup = new EpollEventLoopGroup(
                        Runtime.getRuntime().availableProcessors() * workGroupMultiple);
            }

            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.channel(EpollServerSocketChannel.class);
            serverBootstrap.group(bossGroup, workerGroup);

            serverBootstrap.option(ChannelOption.SO_REUSEADDR, true);
            serverBootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
            serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

            setCommonNettyParams(serverBootstrap);
        }
    }

    private void setCommonNettyParams(ServerBootstrap serverBootstrap) throws InterruptedException {
        // 这个地方和操作系统的保持一致
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 65535);
        serverBootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        serverBootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);

        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) {
                int maxByteLength = conf.getMaxEventSize();
                ch.pipeline().addLast("decoder", new HttpServerCodec(maxByteLength, 8192, 8192));
                ch.pipeline().addLast("aggregator", new HttpObjectAggregator(maxByteLength));
                ch.pipeline().addLast(workerGroup, new HttpBusinessHandler());

            }
        });

        int port = conf.getSourceServerPort(getId().getTag());
        ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
        channelList.add(channelFuture.channel());
        log.info("http server started and listen on " + port);
    }

    @Override
    public void stop() {
        super.stop();

        try {
            preStopByHealthCheck();
        } catch (Exception e) {
            log.error("preStopByHealthCheck exception, logging", e);
        }

        for (Channel channel : channelList) {
            try {
                channel.close();
            } catch (Exception ex) {
                log.error("close channel failed", ex);
            }
        }

        for (EventLoopGroup bossGroup : bossGroupList) {
            try {
                bossGroup.shutdownGracefully().sync();
            } catch (InterruptedException ex) {
                log.error("close bossGroup failed", ex);
            }
        }

        try {
            workerGroup.shutdownGracefully().sync();
        } catch (InterruptedException ex) {
            log.error("close workerGroup failed", ex);
        }

    }

    private void preStopByHealthCheck() throws InterruptedException {
        log.info("pre stop by healthCheck start");

        long sleepTime = 0;
        long initCount = requestAfterStop.get();
        while (sleepTime < 10 * GeneralConstant.ONE_SECOND) {
            sleepTime += GeneralConstant.ONE_SECOND;
            Thread.sleep(sleepTime);
            long newCount = requestAfterStop.get();

            if (newCount == initCount) {
                break;
            }
            initCount = newCount;
        }

        log.info("pre stop by healthCheck end, sleepTime is {}", sleepTime);
    }

    class HttpBusinessHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
            HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

            if (getState() == LifecycleState.STOP) {
                requestAfterStop.incrementAndGet();
            }

            if (msg.uri().startsWith(HEALTH_CHECK_PATH)) {
                if (getState() == LifecycleState.STOP) {
                    response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE);
                }
            } else {
                for (String path : whiteListPath) {
                    if (msg.uri().startsWith(path)) {
                        String logId = msg.headers().get(GeneralConstant.LOG_ID, "");
                        HttpCallback callback = new HttpCallback(logId, ctx);

                        if (!preCheck(logId, callback)) {
                            return;
                        }

                        StreamEvent.SimpleEvent event;
                        try {
                            event = StreamEvent.SimpleEvent.parseFrom(buildBody(msg));
                        } catch (InvalidProtocolBufferException e) {
                            callback.onException("parse_pb_error");
                            return;
                        }

                        process(event, callback);
                        return;
                    }
                }

                // 非白名单 url path 访问
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
            }

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        private byte[] buildBody(FullHttpRequest msg) {
            ByteBuf contentBuf = msg.content();
            Integer length = contentBuf == null ? 0 : contentBuf.readableBytes();
            if (length == 0) {
                log.warn("data content is empty, url:{}, method:{}, header:{}",
                        msg.uri(), msg.method(), msg.headers());
                return new byte[0];
            }
            byte[] content = new byte[length];
            contentBuf.getBytes(0, content);
            return content;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("HttpBusinessHandler catch error:{}", cause);
        }

    }

    class HttpCallback extends AbstractCallback {

        private ChannelHandlerContext ctx;

        HttpCallback(String logId, ChannelHandlerContext ctx) {
            this.logId = logId;
            this.ctx = ctx;
        }

        @Override
        public void onSuccess(long size) {
            super.onSuccess(size);
            final HttpResponse response =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void onException(String msg) {
            super.onException(msg);
            final FullHttpResponse response =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);

            if(eventMeta != null){
                if (eventMeta.getRetryAfter() >= 0) {
                    response.headers().set(GeneralConstant.X_LB_RETRY_AFTER, String.valueOf(eventMeta.getRetryAfter()));
                }
                if (eventMeta.getRetryDelay() >= 0) {
                    response.headers().set(GeneralConstant.X_LB_RETRY_DELAY, String.valueOf(eventMeta.getRetryDelay()));
                }
            }


            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void onLoadFull(String msg) {
            super.onLoadFull(msg);
            final FullHttpResponse response =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TOO_MANY_REQUESTS);

            if (eventMeta.getRetryAfter() >= 0) {
                response.headers().set(GeneralConstant.X_LB_RETRY_AFTER, String.valueOf(eventMeta.getRetryAfter()));
            }
            if (eventMeta.getRetryDelay() >= 0) {
                response.headers().set(GeneralConstant.X_LB_RETRY_DELAY, String.valueOf(eventMeta.getRetryDelay()));
            }

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
