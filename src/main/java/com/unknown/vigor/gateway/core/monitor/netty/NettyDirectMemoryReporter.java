package com.unknown.vigor.gateway.core.monitor.netty;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.internal.PlatformDependent;
import io.prometheus.client.Gauge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class NettyDirectMemoryReporter {

    private static final Gauge NETTY_DIRECT_MEMORY_GAUGE = Gauge.build()
            .name("netty_direct_memory_gauge")
            .help("netty direct memory gauge")
            .register();

    private AtomicLong directMemory;

    private ScheduledExecutorService reporter;

    public void start() {
        reflectField();
        reporter = new ScheduledThreadPoolExecutor(1,
                new ThreadFactoryBuilder().setNameFormat("netty-direct-memory-reporter").build());
        reporter.scheduleWithFixedDelay(() -> NETTY_DIRECT_MEMORY_GAUGE.set(directMemory.get()),
                10, 60, TimeUnit.SECONDS);
    }

    private void reflectField() {
        Field field = ReflectionUtils.findField(PlatformDependent.class, "DIRECT_MEMORY_COUNTER");
        if (field != null) {
            try {
                field.setAccessible(true);
                directMemory = (AtomicLong) field.get(PlatformDependent.class);
                log.info("netty direct memory will be metric.");
            } catch (Exception e) {
                log.error("cannot get direct memory!", e);
            } finally {
                field.setAccessible(false);
            }
        } else {
            log.error("cannot reflect PlatformDependent class!");
        }
    }

    public void stop() {
        if (reporter != null) {
            reporter.shutdownNow();
        }
    }

}
