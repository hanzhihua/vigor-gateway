package com.unknown.vigor.gateway.core.conf;

import com.alibaba.fastjson.JSON;
import com.unknown.vigor.gateway.common.event.LogIdMetadata;
import com.unknown.vigor.gateway.common.event.QueueKey;
import com.unknown.vigor.gateway.core.LifecycleAware;
import com.unknown.vigor.gateway.core.LifecycleState;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractProxyConf implements LifecycleAware {
    private static final String SOURCE_PREFIX = "source.";
    private static final String DOT_SEPARATOR = "\\.";
    private static final String KAFKA_CONFIG_PREFIX = "kafka-config.";

    protected String configPath;

    protected volatile Properties systemProperties = new Properties();

    private Map<String, Class> classCache = new HashMap<>(16);

    private ScheduledExecutorService updateScheduler;

    private LifecycleState state;

    private AtomicBoolean isInit = new AtomicBoolean(false);

    private volatile Map<String, LogIdMetadata> logIdTopicMap = new HashMap<>();
    private volatile LogIdMetadata DefaultOpsLogTopicMeta;

    @Override
    public void start() throws Exception {
        if (!isInit.compareAndSet(false, true)) {
            throw new RuntimeException("config has been started");
        }
        state = LifecycleState.START;
        // 首先务必更新一次,做下conf的初始化
        update(true);
        startChangeObserver();
    }

    @Override
    public void stop() {
        if (updateScheduler != null) {
            updateScheduler.shutdownNow();
        }
        state = LifecycleState.STOP;
        isInit.set(false);
    }

    public LogIdMetadata getTopicMeta(QueueKey key) {

        if (StringUtils.isBlank(key.getAppId())) {
            return logIdTopicMap.get(key.getLogId());
        }

        return this.DefaultOpsLogTopicMeta;
    }

    public Properties getKafkaConfigs() {
        Properties props = new Properties();

        for (Map.Entry<Object, Object> entry : systemProperties.entrySet()) {
            String key = (String) entry.getKey();
            if (key.startsWith(KAFKA_CONFIG_PREFIX)) {
                props.put(key.replaceFirst(KAFKA_CONFIG_PREFIX, ""), entry.getValue());
            }
        }

        return props;
    }

    @Override
    public String toString() {
        List<String> strProps = new ArrayList<>();
        for (Object objectKey : systemProperties.keySet()) {
            String key = (String) objectKey;
            Object value = systemProperties.get(objectKey);
            strProps.add(key + ":" + value);
        }

        return StringUtils.join(strProps, "\n");
    }

    private void startChangeObserver() {
        if (getConfUpdateFrequency() < 0) {
            log.warn("config auto update closed");
            return;
        }
        updateScheduler = new ScheduledThreadPoolExecutor(1,
                new ThreadFactoryBuilder().setNameFormat("config-update-scheduler").build());
        updateScheduler.scheduleWithFixedDelay(() -> {
            List<ConfState> confChangeStatus = null;
            try {
                confChangeStatus = update(false);
            } catch (Exception e) {
                log.error("update config error, current logIdTopicMap = {}", this.logIdTopicMap, e);
            }
            if (CollectionUtils.isEmpty(confChangeStatus)) {
                return;
            }

        }, getConfUpdateFrequency(), getConfUpdateFrequency(), TimeUnit.SECONDS);
    }

    public List<ConfState> update(boolean isFirst) throws Exception {
        log.info("update config start, isFirst: {}", isFirst);

        List<ConfState> confStates = new ArrayList<>(1);
        systemProperties = updateSystemConfig();
        logIdTopicMap = updateLogIdMetas();

        if (!isFirst) {
            boolean confChanged = false;
            if (confChanged) {
                log.info("system config changed, will restart");
                confStates.add(ConfState.SYSTEM_CONF_CHANGED);
            }
        }

        log.info("update config success");

        return confStates;
    }

    public abstract Properties updateSystemConfig() throws Exception;

    public abstract Map<String, LogIdMetadata> updateLogIdMetas() throws Exception;

    protected Map<String, LogIdMetadata> localLoadLogIdMetas() {
        Map<String, LogIdMetadata> metas = new HashMap<>();

        try {
            String logIdJson = FileUtils.readFileToString(
                    Paths.get(configPath, "logid-metadata.json").toFile(), "UTF-8");
            metas = JSON.parseArray(logIdJson, LogIdMetadata.class)
                    .stream()
                    .collect(Collectors.toMap(LogIdMetadata::getLogId, logIdMetadata -> logIdMetadata));
        } catch (Exception e) {
            log.error("update logId metadata from local failed", e);
        }

        return metas;
    }

    public Set<String> getAllSourceTags() {
        Set<String> sourceTags = new HashSet<>(10);
        for (Object objectKey : systemProperties.keySet()) {
            String key = (String) objectKey;
            if (key.startsWith(SOURCE_PREFIX)) {
                String[] splits = key.split(DOT_SEPARATOR);
                if (splits.length < 2) {
                    continue;
                }
                sourceTags.add(splits[1]);
            }
        }
        return sourceTags;
    }

    public int getConfUpdateFrequency() {
        return getInteger(null, ConfVars.CONF_UPDATE_INTERVAL_SECOND);
    }

    public int getMetricsPrometheusPort() {
        return getInteger(null, ConfVars.METRICS_PROMETHEUS_PORT);
    }

    public Class getSourceType(String name) throws Exception {
        return getClass(name, ConfVars.SOURCE_TYPE);
    }

    public int getSourceServerPort(String name) {
        return getInteger(name, ConfVars.SOURCE_SERVER_PORT);
    }

    public List<String> getHttpWhiteList(String name) {
        return getStringList(name, ConfVars.SOURCE_PATH_WHITE_LIST);
    }

    public int getSinkQueueTimeoutMs() {
        return getInteger(null, ConfVars.SINK_QUEUE_TIMEOUT_MS);
    }

    public Boolean getSourceUseNettyEPoll(String name) {
        if (getNettyBossGroupSize(name) > 1) {
            if (!getBoolean(name, ConfVars.SOURCE_USE_NETTY_EPOLL)) {
                log.info("source: {}. netty boss group size: {} ,more than 1, use epoll model", name, getNettyBossGroupSize(name));
            }
            return true;
        } else {
            return getBoolean(name, ConfVars.SOURCE_USE_NETTY_EPOLL);
        }
    }

    public int getSinkProducerSize() {
        return getInteger(null, ConfVars.SINK_PRODUCER_SIZE);
    }

    public int getSinkQueueSize() {
        return getInteger(null, ConfVars.SINK_QUEUE_SIZE);
    }

    public long getSinkQueueTotalMaxBytes() {
        return getLong(null, ConfVars.SINK_QUEUE_MAX_TOTAL_BYTES);
    }

    public int getProducersPerCluster() { return getInteger(null, ConfVars.PRODUCERS_PER_CLUSTER);}

    public int getSinkTimeSliceMs() {
        return getInteger(null, ConfVars.SINK_TIME_SLICE_MS);
    }

    public int getSinkMaxTimeSliceSize() {
        return getInteger(null, ConfVars.SINK_TIME_SLICE_SIZE_MAX);
    }

    public int getNettyBossGroupSize(String tag) {
        return getInteger(tag, ConfVars.SOURCE_NETTY_BOSS_GROUP_SIZE);
    }

    public int getNettyWorkGroupMultiple(String tag) {
        return getInteger(tag, ConfVars.SOURCE_NETTY_WORK_GROUP_MULTIPLE);
    }


    public int getMaxEventSize() {
        return getInteger(null, ConfVars.MAX_EVENT_SIZE);
    }

    public int getKafkaMaxRequestSize() {
        return getInteger(null, ConfVars.KAFKA_MAX_REQUEST_SIZE);
    }

    private Class getOrLoadClass(String className) throws Exception {
        if (classCache.containsKey(className)) {
            return classCache.get(className);
        }
        Class realClass = Class.forName(className);
        classCache.put(className, realClass);
        return realClass;
    }

    protected String replaceWildcard(String varName, String... value) {
        if (value == null) {
            return varName;
        }
        return String.format(varName, value);
    }

    private int getInteger(String tag, ConfVars confVar) {
        return getInteger(tag, confVar, systemProperties);
    }

    private long getLong(String tag, ConfVars confVar) {
        return getLong(tag, confVar, systemProperties);
    }

    private long getLong(String tag, ConfVars confVar, Properties properties) {
        String name = replaceWildcard(confVar.name, tag);
        if (properties.containsKey(name)) {
            String value = properties.getProperty(name);
            if (value != null && !value.isEmpty()) {
                try {
                    return Long.valueOf(value).longValue();
                } catch (NumberFormatException e) {
                }
            }
        }

        return (Long) confVar.defaultValue;
    }

    private int getInteger(String tag, ConfVars confVar, Properties properties) {
        String name = replaceWildcard(confVar.name, tag);
        if (properties.containsKey(name)) {
            String value = properties.getProperty(name);
            if (value != null && !value.isEmpty()) {
                try {
                    return Integer.valueOf(value).intValue();
                } catch (NumberFormatException e) {
                }
            }
        }

        return (Integer) confVar.defaultValue;
    }

    private Double getDouble(String tag, ConfVars confVar) {
        return getDouble(tag, confVar, systemProperties);
    }

    private Double getDouble(String tag, ConfVars confVar, Properties properties) {
        String name = replaceWildcard(confVar.name, tag);
        if (properties.containsKey(name)) {
            return Double.valueOf((String) properties.get(name));
        }

        return Double.valueOf((Double) confVar.defaultValue);
    }

    private Boolean getBoolean(String tag, ConfVars confVar) {
        String name = replaceWildcard(confVar.name, tag);
        if (systemProperties.containsKey(name)) {
            return Boolean.valueOf((String) systemProperties.get(name));
        }

        return (Boolean) confVar.defaultValue;
    }

    private String getString(String tag, ConfVars confVar) {
        return getString(tag, confVar, systemProperties);
    }

    private String getString(String tag, ConfVars confVar, Properties properties) {
        String name = replaceWildcard(confVar.name, tag);
        if (properties.containsKey(name)) {
            return (String) properties.get(name);
        }

        return (String) confVar.defaultValue;
    }

    private List<String> getStringList(String tag, ConfVars confVar) {
        return getStringList(tag, confVar, systemProperties);
    }

    private List<String> getStringList(String tag, ConfVars confVar, Properties properties) {
        String value = getString(tag, confVar, properties);
        if (StringUtils.isEmpty(value)) {
            return Collections.EMPTY_LIST;
        }
        return Arrays.asList(value.split(","));
    }

    private Class getClass(String tag, ConfVars confVar) throws Exception {
        String className = getString(tag, confVar);
        return getOrLoadClass(className);
    }

    @Override
    public LifecycleState getState() {
        return state;
    }

}
