package com.unknown.vigor.gateway.core.monitor.kafka;

import com.unknown.vigor.gateway.common.constant.KafkaMetricsConstants;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;


public class KafkaConsumerJmxCollector extends KafkaJmxCollector {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerJmxCollector.class);

    private volatile static KafkaConsumerJmxCollector instance;

    private KafkaConsumerJmxCollector() {
    }

    public static KafkaConsumerJmxCollector getInstance() {
        if (instance == null) {
            synchronized (KafkaConsumerJmxCollector.class) {
                if (instance == null) {
                    instance = new KafkaConsumerJmxCollector();
                    instance.register();
                    LOG.info("KafkaConsumerJmxCollector started!");
                }
            }
        }
        return instance;
    }

    @Override
    protected void registerKafkaMetric(List<MetricFamilySamples> mfs) {

        KafkaConsumer consumer = new KafkaConsumer(getDefaultKafkaProperties());
        try {
            Map<MetricName, ? extends Metric> metricMap = consumer.metrics();
            // 注册 metric
            addMetricFamily(mfs, metricMap);
        } finally {
            consumer.close();
        }

    }

    @Override
    protected void updateKafkaMetric(List<MetricFamilySamples> mfs, KafkaJmxBean bean) {
        Map<MetricName, ? extends Metric> metricMap = bean.getConsumerMetrics();

        if (metricMap != null) {
            // iterator metric
            for (MetricName keyMetric : metricMap.keySet()) {
                Metric metric = metricMap.get(keyMetric);
                MetricName metricName = metric.metricName();
                String group = metricName.group();
                // format metric name
                String formatMetricName = replaceSpecialChar("kafka-" + metric.metricName().name());
                if ((group.equals(KafkaMetricsConstants.CONSUMER_COORDINATOR_METRICS) &&
                        KafkaMetricsConstants.CONSUMER_COORDINATOR_METRICS_LIST.contains(formatMetricName))
                        || (group.equals(KafkaMetricsConstants.CONSUMER_METRICS) &&
                        KafkaMetricsConstants.CONSUMER_METRICS_LIST.contains(formatMetricName))) {
                    addKafkaMetrics(mfs, metric);
                } else if (group.equals(KafkaMetricsConstants.CONSUMER_FETCH_METRICS)) {
                    // check if tag contains topic
                    Set<String> keySet = metricName.tags().keySet();
                    if ((!keySet.contains("topic") && KafkaMetricsConstants.CONSUMER_FETCH_METRICS_LIST.contains(formatMetricName))
                            || (keySet.contains("topic") && KafkaMetricsConstants.CONSUMER_FETCH_TOPIC_METRICS_LIST.contains(formatMetricName))) {
                        addKafkaMetrics(mfs, metric);
                    }
                }
            }
        }
    }

}
