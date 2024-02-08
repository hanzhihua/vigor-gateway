package com.unknown.vigor.gateway.core.source;

import com.unknown.vigor.gateway.common.constant.GeneralConstant;
import com.unknown.vigor.gateway.common.protobuf.StreamEvent;
import com.unknown.vigor.gateway.common.utils.BackOffUtils;
import com.unknown.vigor.gateway.core.BaseService;
import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;
import com.unknown.vigor.gateway.core.monitor.PrometheusService;
import com.unknown.vigor.gateway.core.sink.KafkaSink;
import com.unknown.vigor.gateway.common.event.*;
import io.prometheus.client.Collector;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public abstract class AbstractSource extends BaseService {

    private int maxKafkaRequestSize;
    private KafkaSink sink;

    @Override
    public void configure(AbstractProxyConf conf) {
       super.configure(conf);
       this.maxKafkaRequestSize = conf.getKafkaMaxRequestSize();
    }

    protected boolean preCheck(String logId, Callback callback) {
        PrometheusService.EVENT_COUNTER.labels(logId, "input", "").inc();

        if (!verify(logId)) {
            callback.onException("illegal_log_id");
            return false;
        }

        return true;
    }

    protected void process(StreamEvent.SimpleEvent originEvent, AbstractCallback callback) {
        long current = System.currentTimeMillis();

        EventMeta eventMeta = new EventMeta(originEvent.getMetaMap());
        eventMeta.setLogId(originEvent.getLogId());
        eventMeta.setCreateTime(originEvent.getCtime());
        eventMeta.setReceiveTime(current);

        LogIdEvent event = new LogIdEvent(originEvent.getLogId(), eventMeta, originEvent.getData().toByteArray());

        callback.setHeaders(eventMeta);
        event.setCallback(callback);

        QueueKey queueKey = new QueueKey(event);
        LogIdMetadata meta = conf.getTopicMeta(queueKey);
        if (meta == null) {
            event.getCallback().onException("on_invalid_meta");
            return;
        }
        PrometheusService.EVENT_BYTES_COUNTER.labels(event.getLogId(), "input").inc(event.size());

        // size is too large, drop it
        int size = event.size();
        if (size > maxKafkaRequestSize) {
            event.getCallback().onSuccess(size);
            PrometheusService.EVENT_SIZE_EXCEED.labels(event.getLogId(), queueKey.getAppId()).inc();
            PrometheusService.EVENT_BYTES_DROP.labels(event.getLogId(), "drop", "too_large").inc(event.size());//丢弃大小
            PrometheusService.EVENT_DROP.labels(event.getLogId(), "drop", "too_large").inc();//丢弃条数
            log.error("logId:{}, appId:{}, message bytes {} larger than max.request.size {}, drop it", event.getLogId(), eventMeta.getAppId(), event.size(), maxKafkaRequestSize);
            return;
        }

        if (sink.send(queueKey, meta, event)) {
            return;
        }

        event.getMeta().setRetryAfter(BackOffUtils.retryTime(sink.retries(queueKey)));
        event.getCallback().onLoadFull("sink_queue_full");
        PrometheusService.EVENT_BYTES_DROP.labels(event.getLogId(), "drop", "queue_full").inc(event.size());
        PrometheusService.EVENT_DROP.labels(event.getLogId(), "drop", "queue_full").inc();
    }

    private boolean verify(String logId) {
        if (logId == null || logId.length() != GeneralConstant.LOG_ID_LENGTH) {
            return false;
        }

        return StringUtils.isNumeric(logId);
    }

    public void setSink(KafkaSink sink) {
        this.sink = sink;
    }

    abstract class AbstractCallback implements Callback {
        protected String logId;
        protected EventMeta eventMeta;

        public void setHeaders(EventMeta eventMeta) {
            this.eventMeta = eventMeta;
        }

        @Override
        public void onSuccess(long size) {
            PrometheusService.EVENT_COUNTER.labels(logId, "output",
                    String.valueOf(StreamEvent.StatusCode.SUCCESS_VALUE)).inc();
            PrometheusService.EVENT_BYTES_COUNTER.labels(logId, "output").inc(size);

            if (eventMeta != null) {
                long duration = System.currentTimeMillis() - eventMeta.getReceiveTime();
                PrometheusService.EVENT_COST_HISTOGRAM.labels(logId).observe(duration / Collector.MILLISECONDS_PER_SECOND);
            }
        }

        @Override
        public void onException(String msg) {
            PrometheusService.EVENT_COUNTER.labels(logId, msg,
                    String.valueOf(StreamEvent.StatusCode.BAD_REQUEST_VALUE)).inc();

        }

        @Override
        public void onLoadFull(String msg) {
            PrometheusService.EVENT_COUNTER.labels(logId, msg,
                    String.valueOf(StreamEvent.StatusCode.LOAD_FULL_VALUE)).inc();
        }
    }

}
