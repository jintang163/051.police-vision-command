package com.police.vision.flink.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NightActiveVehicleResult implements Serializable {

    private String alertId;

    private String alertNo;

    private String plateNo;

    private String vehicleType;

    private String vehicleColor;

    private Integer nightCaptureCount;

    private Integer dayCaptureCount;

    private BigDecimal nightDayRatio;

    private List<String> crossingIds;

    private List<String> crossingNames;

    private LocalDateTime statisticsStartTime;

    private LocalDateTime statisticsEndTime;

    private Integer alertLevel;

    private String description;

    private Long timestamp;

    private BigDecimal lastLongitude;

    private BigDecimal lastLatitude;

    private LocalDateTime lastCaptureTime;
}
