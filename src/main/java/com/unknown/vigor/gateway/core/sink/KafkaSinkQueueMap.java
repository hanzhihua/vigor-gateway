package com.unknown.vigor.gateway.core.sink;

import com.unknown.vigor.gateway.common.event.LogIdEvent;
import com.unknown.vigor.gateway.common.event.LogIdMetadata;
import com.unknown.vigor.gateway.common.event.QueueKey;
import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;
import com.unknown.vigor.gateway.core.limiter.FlowRateLimiter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KafkaSinkQueueMap {
    private AbstractProxyConf conf;

    private Map<QueueKey, KafkaSinkQueue> m;

    private FlowRateLimiter limiter;
    private int maxQueueSize;

    public KafkaSinkQueueMap(AbstractProxyConf conf) {
        this.conf = conf;
        this.m = new ConcurrentHashMap<>();
        this.maxQueueSize = conf.getSinkQueueSize();
        this.limiter = FlowRateLimiter.getSingleton(conf);
    }

    public boolean offer(QueueKey queueKey, LogIdMetadata topicMeta, LogIdEvent event) {
        KafkaSinkQueue q = m.computeIfAbsent(queueKey, key -> new KafkaSinkQueue(conf, key, maxQueueSize, limiter));
        q.updateTopicMetadata(topicMeta);
        return q.offer(event);
    }

    public Set<Map.Entry<QueueKey, KafkaSinkQueue>> entrySet() {
        return m.entrySet();
    }

    public boolean isEmpty() {
        return m.isEmpty();
    }

    public int retries(QueueKey queueKey) {
        return m.get(queueKey).retries();
    }
    
}
