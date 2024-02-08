package com.unknown.vigor.gateway.core.monitor;

import com.unknown.vigor.gateway.core.BaseService;
import com.unknown.vigor.gateway.core.monitor.gc.GCMonitor;
import com.unknown.vigor.gateway.core.monitor.netty.NettyDirectMemoryReporter;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrometheusService extends BaseService {

    public static final Counter EVENT_COUNTER = Counter.build()
            .name("event_counter")
            .help("event counter")
            .labelNames("log_id", "type", "response")
            .register();
    public static final Counter EVENT_BYTES_COUNTER = Counter.build()
            .name("event_bytes_counter")
            .help("event bytes counter")
            .labelNames("log_id", "type")
            .register();


    public static final Counter EVENT_DROP = Counter.build()
            .name("event_drop_counter")
            .help("event drop_counter")
            .labelNames("log_id", "type", "reason")
            .register();

    public static final Counter EVENT_BYTES_DROP = Counter.build()
            .name("event_bytes_drop_counter")
            .help("event bytes_drop_counter")
            .labelNames("log_id", "type", "reason")
            .register();

    public static final Histogram EVENT_COST_HISTOGRAM = Histogram.build()
            .name("event_cost_histogram")
            .help("event cost histogram")
            .labelNames("log_id")
            .register();

    public static final Gauge SINK_QUEUE_GAUGE = Gauge.build()
            .name("sink_queue_gauge")
            .help("sink queue gauge")
            .labelNames("log_id")
            .register();

    public static final Gauge SINK_QUEUE_USAGE_BYTES = Gauge.build()
            .name("sink_queue_usage_bytes_gauge")
            .help("sink queue usage bytes")
            .register();

    public static final Histogram SINK_SEND_TASK_CALLBACK_COST = Histogram.build()
            .name("sink_send_task_callback_cost")
            .help("sink send task callback cost")
            .register();

    public static final Counter EVENT_SIZE_EXCEED = Counter.build()
            .name("event_size_exceed")
            .help("event size exceed")
            .labelNames("log_id", "app_id")
            .register();

    public static final Histogram PRODUCER_SEND_TIME = Histogram.build()
            .name("producer_send_time")
            .help("producer send time")
            .labelNames("logId")
            .register();

    public static final Histogram TOPIC_SEND_TIME = Histogram.build()
            .name("topic_send_time")
            .help("topic send time")
            .labelNames("topic")
            .register();

    public static final Counter KAFKA_ERROR = Counter.build()
            .name("kafka_error")
            .help("kafka_error")
            .labelNames("log_id", "cluster", "topic")
            .register();

    public static final Counter FLOW_LIMIT_TRIGGER = Counter.build()
            .name("flow_limit_trigger")
            .help("flow_limit_trigger")
            .labelNames("log_id")
            .register();

    public static final double KAFKA_FLUSH_QUANTILE = 0.9;
    public static final Summary KAFKA_FLUSH_SUMMARY = Summary.build()
            .quantile(KAFKA_FLUSH_QUANTILE, 0.01)
            .name("kafka_flush_summary")
            .help("kafka flush summary")
            .labelNames("log_id", "topic", "cluster")
            .register();

    public static final Summary QUEUE_WAITING_TIME = Summary.build()
            .quantile(0.5, 0.01)
            .quantile(0.9, 0.01)
            .quantile(0.95, 0.01)
            .quantile(0.99, 0.01)
            .name("queue_waiting_time")
            .help("queue waiting time")
            .register();

    private HTTPServer httpServer;
    private NettyDirectMemoryReporter directMemoryReporter;

    @Override
    public void start() throws Exception {
        super.start();

        int port = conf.getMetricsPrometheusPort();
        httpServer = new HTTPServer(port);
        DefaultExports.initialize();

        GCMonitor.start();

        directMemoryReporter = new NettyDirectMemoryReporter();
        directMemoryReporter.start();

        log.info("prometheus server started and listen on " + port);
    }

    @Override
    public void stop() {
        if (httpServer != null) {
            httpServer.stop();
        }

        if (directMemoryReporter != null) {
            directMemoryReporter.stop();
        }

        super.stop();
    }
}
