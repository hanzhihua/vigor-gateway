package com.unknown.vigor.gateway.core.monitor.kafka;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.GaugeMetricFamily;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class KafkaJmxCollector extends Collector {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaJmxCollector.class);

    // 是否注册
    private volatile boolean isRegistered = false;

    // 要上传监控信息的 KafkaJmxBean 列表
    private final List<KafkaJmxBean> kafkaJmxBeans = new CopyOnWriteArrayList<>();

    // 全局变量，保证注册的监控指标不重复
    private static final Set uniqueMetrics = new HashSet<String>();

    @Override
    public <T extends Collector> T register() {
        T register = register(CollectorRegistry.defaultRegistry);
        return register;
    }


    public void register(KafkaJmxBean bean) {
        kafkaJmxBeans.add(bean);
    }

    public void unregister(KafkaJmxBean bean) {
        kafkaJmxBeans.remove(bean);
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();

        if (!isRegistered) {
            // 未注册成功前，多个 consumer 就可能会同时访问，加同步，并且 double check
            synchronized (KafkaJmxCollector.class) {
                if (!isRegistered) {
                    registerKafkaMetric(mfs);
                    isRegistered = true;
                }
            }

        } else {
            try {
                // 如果存在并发读写集合的情况，使用 Iterator 遍历
                // 否则会产生 ConcurrentModificationException 异常
                for (Iterator<KafkaJmxBean> it = kafkaJmxBeans.iterator(); it.hasNext(); ) {
                    KafkaJmxBean bean = it.next();
                    updateKafkaMetric(mfs, bean);
                }
            } catch (Exception e) {
                LOG.error("Collect error!", e);
            }
        }

        return mfs;
    }

    protected void registerKafkaMetric(List<MetricFamilySamples> mfs) {
        throw new UnsupportedOperationException("unSupportOperation");
    }

    protected void updateKafkaMetric(List<MetricFamilySamples> mfs, KafkaJmxBean bean) {
        throw new UnsupportedOperationException("unSupportOperation");
    }

    protected void addMetricFamily(List<MetricFamilySamples> sampleFamilies,
                                   Map<MetricName, ? extends Metric> metricMap) {

        for (MetricName keyMetric : metricMap.keySet()) {

            if (!uniqueMetrics.contains(keyMetric.name())) {
                GaugeMetricFamily kafkaMetricFamily = new GaugeMetricFamily(replaceSpecialChar("kafka-" + keyMetric.name()),
                        keyMetric.description(), Arrays.asList());

                sampleFamilies.add(kafkaMetricFamily);
                uniqueMetrics.add(keyMetric.name());
            }

        }

    }


    /**
     * 将 Kafka 指标加入指标组中
     *
     * @param mfs
     * @param metric
     */
    protected void addKafkaMetrics(List<MetricFamilySamples> mfs, Metric metric) {

        MetricName metricName = metric.metricName();
        Map<String, String> metricTags = metricName.tags();
        List<String> labelNames = new ArrayList<>(Arrays.asList("group"));
        List<String> labelValues = new ArrayList<>(Arrays.asList(metricName.group()));

        for (String tagKey : metricTags.keySet()) {
            labelNames.add(replaceSpecialChar(tagKey));
            labelValues.add(metricTags.get(tagKey));
        }

        GaugeMetricFamily kafkaMetricFamily = new GaugeMetricFamily(
                replaceSpecialChar("kafka-" + metricName.name()), metricName.description(), labelNames);
        mfs.add(kafkaMetricFamily);
        kafkaMetricFamily.addMetric(labelValues, metric.value());

    }


    protected String replaceSpecialChar(String oldStr) {
        oldStr = oldStr.replaceAll(" ", "_");
        oldStr = oldStr.replaceAll("-", "_");

        return oldStr;
    }

    private String humpToUnderline(String para) {
        StringBuilder sb = new StringBuilder(para);
        int temp = 0;
        if (!para.contains("_")) {
            for (int i = 0; i < para.length(); i++) {
                if (Character.isUpperCase(para.charAt(i))) {
                    sb.insert(i + temp, "_");
                    temp += 1;
                }
            }
        }
        return sb.toString().toLowerCase();
    }

    protected Properties getDefaultKafkaProperties() {
        Properties prop = new Properties();
        prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prop.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        prop.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return prop;
    }

}
