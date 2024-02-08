package com.unknown.vigor.gateway.core.sink;

import com.unknown.vigor.gateway.common.event.LogIdEvent;
import com.unknown.vigor.gateway.common.event.LogIdMetadata;
import com.unknown.vigor.gateway.common.event.OpsLogEvent;
import com.unknown.vigor.gateway.common.event.QueueKey;
import com.unknown.vigor.gateway.core.BaseService;
import com.unknown.vigor.gateway.core.LifecycleState;
import com.unknown.vigor.gateway.core.monitor.PrometheusService;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

@Slf4j
public class SenderTask extends BaseService {
    private String kafkaCluster;
    private AdvancedKafkaProducer kafkaProducer;
    private KafkaSinkQueueMap logIdQueueMap;

    private long queueTimeout;

    public SenderTask(AdvancedKafkaProducer producer, String kafkaCluster, KafkaSinkQueueMap logIdQueueMap) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(kafkaCluster), "kafka cluster is empty");

        this.kafkaProducer = producer;
        this.kafkaCluster = kafkaCluster;
        this.logIdQueueMap = logIdQueueMap;
    }

    @Override
    public void start() throws Exception {
        super.start();
        queueTimeout = conf.getSinkQueueTimeoutMs();

        Thread dispatcher = new TimeSliceDispatcher(conf.getSinkTimeSliceMs(), conf.getSinkMaxTimeSliceSize());
        dispatcher.setName(getId().getFullName());
        dispatcher.start();
    }


    private int send(KafkaSinkQueue queue, LogIdMetadata meta, long timeWindow) {
        int receiveCount = 0;
        int sendCount = 0;

        long start = System.currentTimeMillis();
        long current = start;
        do {
            LogIdEvent event = queue.poll();
            if (event == null) break;

            receiveCount++;
            // check if timeout in queue
            long cost = System.currentTimeMillis() - event.getMeta().getReceiveTime();
            PrometheusService.QUEUE_WAITING_TIME.observe(cost);
            if (cost >= queueTimeout) {
                event.getCallback().onLoadFull("hunger_in_sink_queue");
            } else {
                doSend(event, meta);
                sendCount++;
            }
            current = System.currentTimeMillis();
        } while (current < start + timeWindow);

        if (sendCount > 0) {
            PrometheusService.KAFKA_FLUSH_SUMMARY
                    .labels(meta.getLogId(), meta.getTopic(), meta.getKafkaClusterName())
                    .observe(current - start);
        }

        return receiveCount;
    }

    private void doSend(LogIdEvent event, LogIdMetadata topicMeta) {
        try {
            event.setSendTime(System.currentTimeMillis());
            kafkaProducer.send(convertToProducerRecord(event, topicMeta), ((metadata, exception) -> {
                long current = System.currentTimeMillis();
                PrometheusService.PRODUCER_SEND_TIME.labels(event.getLogId()).observe(current - event.getSendTime());
                PrometheusService.TOPIC_SEND_TIME.labels(topicMeta.getTopic()).observe(current - event.getSendTime());
                if (exception == null) {
                    event.getCallback().onSuccess(event.size());
                    PrometheusService.SINK_SEND_TASK_CALLBACK_COST.observe(System.currentTimeMillis() - current);
                    return;
                }

                String logId = event.getLogId();
                if (event instanceof OpsLogEvent) {
                    // if event is OpsLogEvent, getLogId return its appId
                    logId = topicMeta.getLogId();
                }

                PrometheusService.KAFKA_ERROR.labels(logId, topicMeta.getKafkaClusterName(), topicMeta.getTopic()).inc();
                PrometheusService.EVENT_BYTES_DROP.labels(logId, "drop", "send_fail").inc(event.size());
                PrometheusService.EVENT_DROP.labels(logId, "drop", "send_fail").inc();

                if (exception instanceof RecordTooLargeException) {
                    event.getCallback().onException("data_too_large");
                }

                log.error("sending to kafka error, topicMeta={}, exception={}", topicMeta, exception);
                PrometheusService.SINK_SEND_TASK_CALLBACK_COST.observe(System.currentTimeMillis() - current);
            }));
        } catch (RecordTooLargeException e) {
            event.getCallback().onException("data_too_large");
        } catch (TimeoutException e) {
            event.getCallback().onLoadFull("producer_send_timeout");
            log.error("{} sending to kafka timeout, exception={}", event.getLogId(), e);
        } catch (Exception e) {
            event.getCallback().onException(e.getMessage());
            log.error("{} sending to kafka unknown exception, exception={}", event.getLogId(), e);
        }

    }

    private ProducerRecord<String, byte[]> convertToProducerRecord(LogIdEvent event, LogIdMetadata topicMeta) {
        ProducerRecord<String, byte[]> producerRecord = new ProducerRecord<>(
                topicMeta.getTopic(), null, null, event.getBody());

        for (Map.Entry<String, String> entry : event.getMeta().toKafka().entrySet()) {
            producerRecord.headers().add(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return producerRecord;
    }

    @Override
    public void stop() {
        super.stop();
    }

    /**
     * producer 的时间片调度器
     */
    private class TimeSliceDispatcher extends Thread {
        private int timeSliceInMills;
        /**
         * 单 logId 可分配的最大分片数
         */
        private int maxAllocatedSlices;

        TimeSliceDispatcher(int duration, int size) {
            this.timeSliceInMills = duration;
            this.maxAllocatedSlices = size;
        }

        @Override
        public void run() {
            while (state == LifecycleState.START) {
                if (doRun()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        log.warn("TimeSliceDispatcher interrupted");
                    }
                }
            }
        }

        private boolean doRun() {
            boolean emptyRun = true;

            for (Iterator<Map.Entry<QueueKey, KafkaSinkQueue>> iterator = logIdQueueMap.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<QueueKey, KafkaSinkQueue> entry = iterator.next();

                QueueKey key = entry.getKey();
                KafkaSinkQueue eventQueue = entry.getValue();

                if (eventQueue.isEmpty()) continue;

                LogIdMetadata topicMeta = eventQueue.getTopicMetadata();

                if (topicMeta == null) {
                    log.warn("SenderTask delete eventQueue when get null topicMeta, logId={}", key);
                    iterator.remove();
                    continue;
                }

                if (!eventQueue.isEmpty()) {
                    try {
                        int processors = eventQueue.enter();
                        if (processors > maxAllocatedSlices) continue;

                        int count = send(eventQueue, topicMeta, timeSliceInMills);
                        if (count > 0) {
                            emptyRun = false;
                        }
                    } catch (Throwable e) {
                        log.error("send task run error, logId={}", key, e);
                    } finally {
                        eventQueue.leave();
                    }
                }
            }

            return emptyRun;
        }
    }

}
