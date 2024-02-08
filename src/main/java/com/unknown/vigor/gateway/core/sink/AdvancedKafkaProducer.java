package com.unknown.vigor.gateway.core.sink;

import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;
import com.unknown.vigor.gateway.core.monitor.kafka.KafkaJmxBean;
import com.unknown.vigor.gateway.core.monitor.kafka.KafkaProducerJmxCollector;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.Future;

@Slf4j
public class AdvancedKafkaProducer {
    private KafkaProducer<String, byte[]> kafkaProducer;
    private KafkaJmxBean jmxBean;

    public AdvancedKafkaProducer(AbstractProxyConf config, String brokerList) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(brokerList), "kafka brokerList is empty");

        Properties props = config.getKafkaConfigs();

        // TODO(livexmm) for gray release, remove below
        props.put("batch.size", 1048576);
        props.put("max.request.size", 10485760);
        props.put("linger.ms", 5);
        props.put("request.timeout.ms", 3000);
        props.put("max.in.flight.requests.per.connection", 10);
        props.remove("partitioner.class");
        props.put("compression.type", "zstd");

        // below config item must exist
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        if (!props.containsKey(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)) {
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        }
        if (!props.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) {
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        }
        log.info("kafka-client config = {}", props);
        kafkaProducer = new KafkaProducer<>(props);

        try {
            jmxBean = new KafkaJmxBean(kafkaProducer.metrics(), null);
            KafkaProducerJmxCollector.getInstance().register(jmxBean);
            log.info("kafka producer register jmx collector");
        } catch (Exception e) {
            log.error("kafka producer jmx register error", e);
        }
    }

    public Future<RecordMetadata> send(ProducerRecord<String, byte[]> record, Callback callback) {
        return kafkaProducer.send(record, callback);
    }

    public void flush() {
        // kafkaProducer.flush();
    }

    public void close() {
        kafkaProducer.close();
        KafkaProducerJmxCollector.getInstance().unregister(jmxBean);
    }
}
