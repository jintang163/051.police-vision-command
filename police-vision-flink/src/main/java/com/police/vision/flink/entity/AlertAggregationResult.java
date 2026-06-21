package com.police.vision.flink.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertAggregationResult implements Serializable {

    private String windowStart;
    private String windowEnd;
    private Integer alertType;
    private String alertTypeName;
    private Long count;
    private String gridCode;
    private Integer level1Count;
    private Integer level2Count;
    private Integer level3Count;
    private Long windowTimestamp;

    public AlertAggregationResult(Integer alertType, Long count) {
        this.alertType = alertType;
        this.count = count;
    }
}
