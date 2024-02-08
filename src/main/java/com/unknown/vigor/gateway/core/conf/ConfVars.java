package com.unknown.vigor.gateway.core.conf;

enum ConfVars {

    /**
     * common config
     **/
    CONF_UPDATE_INTERVAL_SECOND("conf.update.interval.second", "配置参数更新间隔,小于0表示不更新,单位s", 60),
    METRICS_PROMETHEUS_PORT("metrics.prometheus.port", "metrics的Prometheus监控端口", 8000),
    MAX_EVENT_SIZE("max.event.size", "event最大字节数", 67108864),
    KAFKA_MAX_REQUEST_SIZE("kafka-config.max.request.size", "单条记录最大字节数", 10485760),

    /**
     * SOURCE config
     **/
    SOURCE_TYPE("source.%s.type", "source类型,完整class名称",
            "core.com.unknown.vigor.gateway.HttpSource"),
    SOURCE_SERVER_PORT("source.%s.server.port", "source服务端口", 8081),
    SOURCE_USE_NETTY_EPOLL("source.%s.use.netty.epoll", "使用epoll模型", false),
    SOURCE_NETTY_BOSS_GROUP_SIZE("source.%s.netty.boss.group.size", "启动netty boss group的个数,如果个数超过1,将强制覆盖SOURCE_USE_NETTY_EPOLL属性为true", 1),
    SOURCE_NETTY_WORK_GROUP_MULTIPLE("source.%s.netty.work.group.multiple", "启动netty work group的线程的倍数,默认为系统核数,如填2,则为系统核数*2", 1),
    SOURCE_EXECUTOR_CORE_THREAD_NUM("source.%s.executor.core.thread.num",
            "source线程池核心线程数,grpc使用,-1为cache线程池", -1),
    SOURCE_EXECUTOR_MAX_THREAD_NUM("source.%s.executor.max.thread.num",
            "source线程池最大线程数,grpc使用,-1为cache线程池", -1),
    SOURCE_EXECUTOR_QUEUE_CAPACITY("source.%s.executor.queue.capacity",
            "source线程池队列最大容量,grpc使用,-1为cache线程池", -1),
    SOURCE_PATH_WHITE_LIST("source.%s.path.white.list", "http source path 白名单列表", "/"),

    /**
     * SINK config
     **/
    SINK_PRODUCER_SIZE("sink.producer.size", "sink producer 数量", 5),
    SINK_QUEUE_SIZE("sink.queue.size", "sink logId 队列大小", 400),
    SINK_QUEUE_MAX_TOTAL_BYTES("sink.queue.total.bytes", "sink queue max total bytes", 10737418240L),
    SINK_QUEUE_TIMEOUT_MS("sink.queue.timeout.ms", "event 呆在 sink queue 中的超时时间，超过这个时间不再处理直接返回", 1000),
    SINK_TIME_SLICE_MS("sink.time.slice.ms", "sink 时间片长度，单位毫秒", 1000),
    SINK_TIME_SLICE_SIZE_MAX("sink.time.slice.size.max", "sink 单个 logId 最多可分配的时间片个数", 3),

    PRODUCERS_PER_CLUSTER("cluster.producer.num", "each cluster producer number", 4);

    String name;
    String desc;
    Object defaultValue;

    ConfVars(String name, String desc, Object defaultValue) {
        this.name = name;
        this.desc = desc;
        this.defaultValue = defaultValue;
    }

}
