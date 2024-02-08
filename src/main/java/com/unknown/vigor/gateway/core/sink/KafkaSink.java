package com.unknown.vigor.gateway.core.sink;

import com.unknown.vigor.gateway.common.event.LogIdEvent;
import com.unknown.vigor.gateway.common.event.LogIdMetadata;
import com.unknown.vigor.gateway.common.event.QueueKey;
import com.unknown.vigor.gateway.core.BaseService;
import com.unknown.vigor.gateway.core.ComponentId;
import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class KafkaSink extends BaseService {

    private static final int WAIT_STOP_MS_MAX = 10_000;

    private static final int WAIT_STOP_MS_INTERVAL = 100;

    private KafkaSinkQueueMap logIdQueueMap;

    private Map<String, SenderGroup> clusterSenders;

    private int producersPerCluster;

    @Override
    public void configure(AbstractProxyConf conf) {
        super.configure(conf);
        producersPerCluster = conf.getProducersPerCluster();
    }

    @Override
    public void start() throws Exception {
        super.start();
        logIdQueueMap = new KafkaSinkQueueMap(conf);
        clusterSenders = new HashMap<>();
    }

    public boolean send(QueueKey key, LogIdMetadata topicMeta, LogIdEvent event) {
        if (!clusterSenders.containsKey(topicMeta.getKafkaClusterName())) {
            createSenderTasks(topicMeta, conf.getSinkProducerSize());
        }

        return logIdQueueMap.offer(key, topicMeta, event);
    }

    synchronized private void createSenderTasks(LogIdMetadata meta, int taskSize) {
        if (clusterSenders.containsKey(meta.getKafkaClusterName())) return;

        int numProducers = this.producersPerCluster;
        if (numProducers > taskSize) {
            numProducers = taskSize;
        }

        List<AdvancedKafkaProducer> producers = new ArrayList<>();
        for (int i = 0; i < numProducers; i++) {
            producers.add(new AdvancedKafkaProducer(conf, meta.getBrokerList()));
        }

        List<SenderTask> senders = new ArrayList<>(taskSize);
        for (int i = 0; i < taskSize; i++) {
            SenderTask sender;
            try {
                sender = new SenderTask(producers.get(i % numProducers), meta.getKafkaClusterName(), logIdQueueMap);
                sender.configure(conf);
                ComponentId id = new ComponentId(sender.getClass().getSimpleName(), meta.getKafkaClusterName(), i);
                sender.setId(id);
                sender.start();
                log.info("start sender task {} with producer {}", i, i % numProducers);
            } catch (Exception e) {
                log.error("start sender task error logId:{},kafkaCluster:{} ", meta.getLogId(), meta.getKafkaClusterName());
                throw new IllegalStateException("start sender task error", e);
            }

            senders.add(sender);
        }

        clusterSenders.put(meta.getKafkaClusterName(), new SenderGroup(producers, senders));
    }

    @Override
    public void stop() {
        int remainWaitMs = WAIT_STOP_MS_MAX;
        while (!checkQueueEmpty() && remainWaitMs > 0) {
            try {
                Thread.sleep(WAIT_STOP_MS_INTERVAL);
            } catch (InterruptedException e) {
                log.warn("wait stop interrupted");
            }

            remainWaitMs -= WAIT_STOP_MS_INTERVAL;
        }

        clusterSenders.values().forEach(g -> g.stop());

        super.stop();
    }

    private boolean checkQueueEmpty() {
        return logIdQueueMap.isEmpty();
    }

    public int retries(QueueKey queueKey) {
        return logIdQueueMap.retries(queueKey);
    }

}
