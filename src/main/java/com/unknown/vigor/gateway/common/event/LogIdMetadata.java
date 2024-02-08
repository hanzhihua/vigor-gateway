package com.unknown.vigor.gateway.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogIdMetadata {
    private String logId;
    private String topic;
    /**
     * 集群名称
     */
    private String kafkaClusterName;
    private String brokerList;
}
