package com.unknown.vigor.gateway.core;

import com.google.protobuf.ByteString;
import com.unknown.vigor.gateway.common.protobuf.StreamEvent;
import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;
import com.unknown.vigor.gateway.core.conf.DefaultConf;
import com.unknown.vigor.gateway.core.monitor.PrometheusService;
import com.unknown.vigor.gateway.core.sink.KafkaSink;
import com.unknown.vigor.gateway.core.source.AbstractSource;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.springframework.util.CollectionUtils;

import java.util.*;

public abstract class BaseTest {

    protected static final String URL = "http://localhost:5801/log/test";
    protected static final long MAX_EVENT_SIZE = 500;
    protected final Random random = new Random();

    protected AbstractProxyConf conf;
    protected KafkaSink sink;
    protected List<AbstractSource> sources;

    protected PrometheusService prometheusService;


    @Before
    public void init() throws Exception {
        startConf();
        startPrometheus();
        startSink();
        startSource();
    }

    @After
    public void destroy() {
        stopSource();
        stopSink();
        stopPrometheus();
        stopConf();
    }

    protected Response mockSendHttpEvent(String logId, long size) {
        Map<String, String> headers = new HashMap<>();
        headers.put("item-count", "200");
        headers.put("ctime", Long.toString(System.currentTimeMillis()));

        headers.put("logId", logId);
        Response response = HttpClientUtil.sendPost(URL,
                mockPbEvent(logId, size).toByteArray(),
                headers);
        return response;
    }

    protected void startConf() throws Exception {
        conf = new DefaultConf();
        conf.start();
    }

    protected void stopConf() {
        if (conf != null) {
            conf.stop();
        }
    }


    protected void startPrometheus() throws Exception {
        prometheusService = new PrometheusService();
        prometheusService.configure(conf);
        prometheusService.start();
    }

    protected void stopPrometheus() {
        if (prometheusService != null) {
            prometheusService.stop();
        }
    }

    protected void startSink() throws Exception {
        sink = new KafkaSink();
        sink.configure(conf);
        ComponentId sinkId = new ComponentId(sink.getClass().getSimpleName());
        sink.setId(sinkId);
        sink.start();
    }

    protected void stopSink() {
        if (sink != null) {
            sink.stop();
        }
    }

    protected void startSource() throws Exception {
        Set<String> sourceTags = conf.getAllSourceTags();

        sources = new ArrayList<>(sourceTags.size());
        for (String sourceTag : sourceTags) {
            Class<AbstractSource> sourceClass = conf.getSourceType(sourceTag);
            AbstractSource source = sourceClass.newInstance();
            source.configure(conf);
            source.setSink(sink);
            ComponentId sourceId = new ComponentId(source.getClass().getSimpleName(), sourceTag);
            source.setId(sourceId);
            source.start();

            sources.add(source);
        }
    }

    protected void stopSource() {
        if (!CollectionUtils.isEmpty(sources)) {
            for (AbstractSource source : sources) {
                source.stop();
            }
        }
    }

    protected StreamEvent.SimpleEvent mockPbEvent(String logId, long size) {
        long current = System.currentTimeMillis();

        StreamEvent.SimpleEvent.Builder builder = StreamEvent.SimpleEvent.newBuilder();
        builder.setLogId(logId);
        byte[] data = new byte[(int) size];
        random.nextBytes(data);
        builder.setData(ByteString.copyFrom(data));
        builder.setCtime(current);

        Map<String, String> meta = new HashMap<>();
//        meta.put("request-path", "/log/test");
//        meta.put("request_time", Long.toString(current));
//        meta.put("DATA-REAL-IP", "127.0.0.1");
        meta.put("logId", "900000");
        meta.put("ctime", Long.toString(current));
//        meta.put("item-count", "100");
        builder.putAllMeta(meta);

        return builder.build();
    }

}
