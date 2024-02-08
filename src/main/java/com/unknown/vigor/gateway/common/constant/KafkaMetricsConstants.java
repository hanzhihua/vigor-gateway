package com.unknown.vigor.gateway.common.constant;

import java.util.Arrays;
import java.util.List;

public class KafkaMetricsConstants {

    public static final String PRODUCER_METRICS = "producer-metrics";
    public static final String PRODUCER_TOPIC_METRICS = "producer-topic-metrics";
    public static final String CONSUMER_METRICS = "consumer-metrics";
    public static final String CONSUMER_FETCH_METRICS = "consumer-fetch-manager-metrics";
    public static final String CONSUMER_COORDINATOR_METRICS = "consumer-coordinator-metrics";

    public final static List<String> PRODUCER_METRICS_LIST = Arrays.asList(
            "kafka_waiting_threads",
            "kafka_buffer_total_bytes",
            "kafka_buffer_available_bytes",
            "kafka_bufferpool_wait_ratio",
            "kafka_request_latency_avg",
            "kafka_request_latency_max",
            "kafka_requests_in_flight",
            "kafka_batch_size_max",
            "kafka_batch_size_avg",
            "kafka_record_queue_time_max",
            "kafka_record_queue_time_avg",
            "kafka_network_io_rate",
            "kafka_outgoing_byte_rate",
            "kafka_incoming_byte_rate",
            "kafka_connection_count",
            "kafka_request_rate"
    );

    public final static List<String> PRODUCER_TOPIC_METRICS_LIST = Arrays.asList(
            "kafka_record_error_rate",
            "kafka_record_send_rate",
            "kafka_byte_rate",
            "kafka_network_io_rate",
            "kafka_outgoing_byte_rate",
            "kafka_incoming_byte_rate",
            "kafka_connection_count"
    );

    public final static List<String> CONSUMER_METRICS_LIST = Arrays.asList(
            "kafka_network_io_rate",
            "kafka_outgoing_byte_rate",
            "kafka_incoming_byte_rate",
            "kafka_connection_count"
    );

    public final static List<String> CONSUMER_FETCH_METRICS_LIST = Arrays.asList(
            "kafka_fetch_latency_max",
            "kafka_fetch_latency_avg",
            "kafka_fetch_throttle_time_max",
            "kafka_fetch_throttle_time_avg",
            "kafka_network_io_rate",
            "kafka_outgoing_byte_rate"
    );

    public final static List<String> CONSUMER_FETCH_TOPIC_METRICS_LIST = Arrays.asList(
            "kafka_bytes_consumed_rate",
            "kafka_records_consumed_rate",
            "kafka_records_lag",
            "kafka_outgoing_byte_rate"
    );

    public final static List<String> CONSUMER_COORDINATOR_METRICS_LIST = Arrays.asList(
            "kafka_assigned_partitions",
            "kafka_join_time_max",
            "kafka_join_time_avg",
            "kafka_sync_time_max",
            "kafka_sync_time_avg",
            "kafka_sync_rate",
            "kafka_outgoing_byte_rate"
    );

}
