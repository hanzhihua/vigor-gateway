package com.unknown.vigor.gateway;

import com.unknown.vigor.gateway.core.BaseService;
import com.unknown.vigor.gateway.core.ComponentId;
import com.unknown.vigor.gateway.core.conf.DefaultConf;
import com.unknown.vigor.gateway.core.monitor.PrometheusService;
import com.unknown.vigor.gateway.core.sink.KafkaSink;
import com.unknown.vigor.gateway.core.source.AbstractSource;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Slf4j
public class Main extends BaseService {

    private PrometheusService prometheusService;
    private KafkaSink sink;
    private List<AbstractSource> sources;

    public static void main(String[] args) {
        Main proxy = new Main();
        proxy.start();
        Runtime.getRuntime().addShutdownHook(new Thread(proxy::stop, "KafkaProxy-shutdown-hook"));
    }

    @Override
    public void start() {
        log.info("kafka-proxy is starting");

        try {
            startConf();
            startPrometheus();
            startSink();
            startSources();

            super.start();
            log.info("kafka-proxy start success");
        } catch (Throwable e) {
            log.error("kafka-proxy start failed", e);
            stop();
            System.exit(-1);
        }
    }

    @Override
    public void stop() {
        log.info("kafka-proxy is stopping");
        stopSources();
        stopSink();
        stopPrometheus();
        stopConf();
        super.stop();
        log.info("kafka-proxy stop success");
    }


    private void startConf() throws Exception {
        conf = new DefaultConf();
        conf.start();
    }

    private void stopConf() {
        if (conf != null) {
            conf.stop();
        }
    }

    private void startPrometheus() throws Exception {
        prometheusService = new PrometheusService();
        prometheusService.configure(conf);
        prometheusService.start();
    }

    private void stopPrometheus() {
        if (prometheusService != null) {
            prometheusService.stop();
        }
    }

    private void startSink() throws Exception {
        sink = new KafkaSink();
        sink.configure(conf);
        ComponentId sinkId = new ComponentId(sink.getClass().getSimpleName());
        sink.setId(sinkId);
        sink.start();
    }

    private void stopSink() {
        if (sink != null) {
            sink.stop();
        }
    }

    private void startSources() throws Exception {
        Set<String> sourceTags = conf.getAllSourceTags();

        sources = new ArrayList<>(sourceTags.size());
        for (String sourceTag : sourceTags) {
            Class<AbstractSource> sourceClass = conf.getSourceType(sourceTag);
            AbstractSource source = sourceClass.newInstance();
            source.configure(conf);
            source.setSink(sink);
            ComponentId sourceId = new ComponentId(source.getClass().getSimpleName(), sourceTag);
            source.setId(sourceId);
            source.start();

            sources.add(source);
        }
    }

    private void stopSources() {
        if (!CollectionUtils.isEmpty(sources)) {
            for (AbstractSource source : sources) {
                source.stop();
            }
        }
    }

}
