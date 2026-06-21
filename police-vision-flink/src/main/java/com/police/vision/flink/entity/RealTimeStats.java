package com.police.vision.flink.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealTimeStats implements Serializable {

    private Long totalAlarmsToday;
    private Long pendingAlarms;
    private Long processingAlarms;
    private Long completedAlarmsToday;
    private Integer onlinePoliceCount;
    private Integer onlineCameraCount;
    private Map<Integer, Long> alarmTypeStats;
    private Map<Integer, Long> alertLevelStats;
    private Map<String, Long> areaAlarmStats;
    private Long timestamp;

    public RealTimeStats(Long timestamp) {
        this.timestamp = timestamp;
    }
}
