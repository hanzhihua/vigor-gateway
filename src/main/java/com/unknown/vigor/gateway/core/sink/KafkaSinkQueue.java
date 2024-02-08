package com.unknown.vigor.gateway.core.sink;

import com.unknown.vigor.gateway.common.event.LogIdEvent;
import com.unknown.vigor.gateway.common.event.LogIdMetadata;
import com.unknown.vigor.gateway.common.event.QueueKey;
import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;
import com.unknown.vigor.gateway.core.limiter.FlowRateLimiter;
import com.unknown.vigor.gateway.core.monitor.PrometheusService;
import io.prometheus.client.Gauge;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class KafkaSinkQueue {
    private AbstractProxyConf conf;

    private QueueKey key;
    private FlowRateLimiter limiter;

    private LinkedBlockingQueue<LogIdEvent> q;

    private AtomicInteger fullCount;

    private AtomicInteger processors;

    private Gauge.Child queueSizeMetric;

    private volatile LogIdMetadata topicMeta;

    public KafkaSinkQueue(AbstractProxyConf conf, QueueKey key, int maxSize, FlowRateLimiter limiter) {
        this.conf = conf;
        this.key = key;
        this.q = new LinkedBlockingQueue<>(maxSize);
        this.limiter = limiter;
        this.fullCount = new AtomicInteger(0);
        this.processors = new AtomicInteger(0);
        this.queueSizeMetric = PrometheusService.SINK_QUEUE_GAUGE.labels(key.toString());

        this.topicMeta = null;
    }

    public boolean offer(LogIdEvent event) {
        if (!limiter.incr(event.size())) {
            PrometheusService.FLOW_LIMIT_TRIGGER.labels(event.getLogId()).inc();
            return false;
        }

        boolean ok = false;
        try {
            // maybe throw InterruptedException
            ok = q.offer(event);
        } finally {
            if (ok) {
                PrometheusService.SINK_QUEUE_USAGE_BYTES.set(limiter.value());
                queueSizeMetric.set(q.size());
                fullCount.set(0); // reset
            } else {
                limiter.decr(event.size());
                fullCount.incrementAndGet();
            }
        }

        return ok;
    }

    public LogIdEvent poll() {
        LogIdEvent event = q.poll();
        if (event != null) {
            limiter.decr(event.size());
        }
        return event;
    }

    public boolean isEmpty() {
        return q.isEmpty();
    }

    public int size() {
        return q.size();
    }

    public int retries() {
        return fullCount.get();
    }

    public int enter() {
        return this.processors.incrementAndGet();
    }

    public int leave() {
        return this.processors.decrementAndGet();
    }

    public void updateTopicMetadata(LogIdMetadata topicMetadata) {
        this.topicMeta = topicMetadata;
    }

    public LogIdMetadata getTopicMetadata() {
        return topicMeta;
    }
}
