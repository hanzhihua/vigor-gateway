package com.unknown.vigor.gateway.common.event;

import com.unknown.vigor.gateway.common.constant.GeneralConstant;

import java.util.HashMap;
import java.util.Map;

public class EventMeta {

    private String appId;
    private String logId;

    private long createTime;
    private long receiveTime;

    // which meta item will send to kafka
    private Map<String, String> origin;

    // only used for return to client
    private int retryAfter;
    private int retryDelay;

    public EventMeta(Map<String, String> o) {
        origin = new HashMap<>(o);
        appId = origin.getOrDefault(GeneralConstant.OPS_LOG_APPID, "");
        logId = origin.getOrDefault(GeneralConstant.LOG_ID, "");
        createTime = Long.valueOf(origin.getOrDefault(GeneralConstant.CREATE_TIME, "0"));
        receiveTime = Long.valueOf(origin.getOrDefault(GeneralConstant.UPDATE_TIME, "0"));
        retryAfter = -1;
        retryDelay = -1;
    }

    public void setLogId(String logId) {
        this.logId = logId;
        this.origin.put(GeneralConstant.LOG_ID, logId);
    }

    public String getLogId() {
        return logId;
    }

    public String getAppId() {
        return appId;
    }

    public void setCreateTime(long createTime) {
        if (this.createTime == 0) {
            this.createTime = createTime;
            this.origin.put(GeneralConstant.CREATE_TIME, String.valueOf(createTime));
        }
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setReceiveTime(long receiveTime) {
        this.receiveTime = receiveTime;
        this.origin.put(GeneralConstant.UPDATE_TIME, String.valueOf(receiveTime));
        this.origin.put(GeneralConstant.GATEWAY_RECEIVE_TIME, String.valueOf(receiveTime));
    }

    public long getReceiveTime() {
        return receiveTime;
    }

    public Map<String, String> toKafka() {
        return origin;
    }

    public void setRetryAfter(int retryAfter) {
        this.retryAfter = retryAfter;
    }

    public int getRetryAfter() {
        return retryAfter;
    }

    public void setRetryDelay(int retryDelay) {
        this.retryDelay = retryDelay;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public int getSize() {
        int size = 0;
        for (Map.Entry<String, String> entry : origin.entrySet()) {
            if (entry.getKey() != null) {
                size += entry.getKey().length();
            }
            if (entry.getValue() != null) {
                size += entry.getValue().length();
            }
        }
        return size;
    }
}
