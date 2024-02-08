package com.unknown.vigor.gateway.common.event;

import com.google.common.base.Preconditions;
import lombok.Data;

@Data
public class OpsLogEvent extends LogIdEvent {
    private String appId;

    public OpsLogEvent(String logId, EventMeta meta, byte[] body) {
        super(logId, meta, body);
        Preconditions.checkArgument(!meta.getAppId().isEmpty());
        appId = meta.getAppId();
    }
}
