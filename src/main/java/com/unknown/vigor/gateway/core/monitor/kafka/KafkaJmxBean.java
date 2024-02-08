package com.unknown.vigor.gateway.core.monitor.kafka;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

import java.util.Map;


public class KafkaJmxBean {
    private Map<MetricName, ? extends Metric> producer;
    private Map<MetricName, ? extends Metric> consumer;

    public KafkaJmxBean(Map<MetricName, ? extends Metric> producer, Map<MetricName, ? extends Metric> consumer) {
        this.producer = producer;
        this.consumer = consumer;
    }

    public Map<MetricName, ? extends Metric> getProducerMetrics() {
        return producer;
    }

    public Map<MetricName, ? extends Metric> getConsumerMetrics() {
        return consumer;
    }

//    @Override
//    public String toString() {
//        return "KafkaJmxBean{" +
//                "producer=" + producer +
//                ", consumer=" + consumer +
//                '}';
//    }
}
