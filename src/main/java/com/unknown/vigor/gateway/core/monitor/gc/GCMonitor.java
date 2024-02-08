package com.unknown.vigor.gateway.core.monitor.gc;

import com.sun.management.GarbageCollectionNotificationInfo;
import io.prometheus.client.Histogram;
import lombok.extern.slf4j.Slf4j;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class GCMonitor {
    private static final Histogram gcHistogram = Histogram.build()
            .labelNames("gcType")
            .name("gc_time_seconds_histogram")
            .help("GC time in seconds.")
            .register();

    public static void start() {
        log.info("Start monitor GC");

        //get all GarbageCollectorMXBeans
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        //register every GarbageCollectorMXBean
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            log.info("Register {} for {}", gcBean.getName(), Arrays.deepToString(gcBean.getMemoryPoolNames()));

            NotificationEmitter emitter = (NotificationEmitter) gcBean;
            NotificationListener listener = (Notification notification, Object handback) -> {
                //get gc info
                GarbageCollectionNotificationInfo info =
                        GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                //output
                String gcType = info.getGcAction();
                if (gcType.length() > 7) {
                    gcType = gcType.substring(7);
                }
                long gcTime = info.getGcInfo().getEndTime() - info.getGcInfo().getStartTime();
                gcHistogram.labels(gcType).observe((double) gcTime / 1000);
            };
            emitter.addNotificationListener(listener, (Notification notification) -> {
                //filter GC notification
                return notification.getType()
                        .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION);
            }, null);
        }
    }
}
