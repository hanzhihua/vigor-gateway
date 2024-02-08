package com.unknown.vigor.gateway.common.event;

import lombok.Data;

@Data
public class QueueKey {
    private String LogId;
    private String AppId;

    public QueueKey(LogIdEvent event) {
        if (event instanceof OpsLogEvent) {
            OpsLogEvent opsLogEvent = (OpsLogEvent) event;
            this.AppId = opsLogEvent.getAppId();
            this.LogId = opsLogEvent.getLogId();
        } else {
            this.LogId = event.getLogId();
            this.AppId = "";
        }
    }

    @Override
    public boolean equals(Object o){
        return this.LogId.equals(((QueueKey) o).getLogId()) && this.getAppId().equals(((QueueKey) o).getAppId());
    }

    @Override
    public int hashCode(){
        return (this.getLogId() + this.getAppId()).hashCode();
    }

    @Override
    public String toString() {
        if ("".equals(this.getAppId())) {
            return this.getLogId();
        }
        return this.getLogId() + "#" + this.getAppId();
    }
}
