package com.unknown.vigor.gateway.core.monitor.kafka;

import com.unknown.vigor.gateway.common.constant.KafkaMetricsConstants;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;


public class KafkaProducerJmxCollector extends KafkaJmxCollector {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerJmxCollector.class);

    private volatile static KafkaProducerJmxCollector instance;

    private KafkaProducerJmxCollector() {
    }

    public static KafkaProducerJmxCollector getInstance() {
        if (instance == null) {
            synchronized (KafkaProducerJmxCollector.class) {
                if (instance == null) {
                    instance = new KafkaProducerJmxCollector();
                    instance.register();
                    LOG.info("KafkaProducerJmxCollector started!");
                }
            }
        }
        return instance;
    }

    @Override
    protected void registerKafkaMetric(List<MetricFamilySamples> mfs) {

        KafkaProducer producer = new KafkaProducer(getDefaultKafkaProperties());
        try {
            Map<MetricName, ? extends Metric> metricMap = producer.metrics();
            // 注册 metric
            addMetricFamily(mfs, metricMap);
        } finally {
            producer.close();
        }

    }

    @Override
    protected void updateKafkaMetric(List<MetricFamilySamples> mfs, KafkaJmxBean bean) {
        Map<MetricName, ? extends Metric> metricMap = bean.getProducerMetrics();

        if (metricMap != null) {
            // iterator metrics
            for (MetricName keyMetric : metricMap.keySet()) {
                Metric metric = metricMap.get(keyMetric);
                MetricName metricName = metric.metricName();
                String group = metricName.group();
                // format metric name
                String formatMetricName = replaceSpecialChar("kafka-" + metric.metricName().name());

                if ((group.equals(KafkaMetricsConstants.PRODUCER_METRICS) &&
                        KafkaMetricsConstants.PRODUCER_METRICS_LIST.contains(formatMetricName))
                        || (group.equals(KafkaMetricsConstants.PRODUCER_TOPIC_METRICS) &&
                        KafkaMetricsConstants.PRODUCER_TOPIC_METRICS_LIST.contains(formatMetricName))) {
                    addKafkaMetrics(mfs, metric);
                }
            }
        }
    }

}
