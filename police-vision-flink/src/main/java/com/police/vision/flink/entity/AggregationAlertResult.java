package com.police.vision.flink.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregationAlertResult implements Serializable {

    private String alertId;

    private String alertNo;

    private String areaCode;

    private String areaName;

    private BigDecimal centerLongitude;

    private BigDecimal centerLatitude;

    private Integer totalPersonCount;

    private Integer targetPersonCount;

    private List<String> personIds;

    private List<String> personNames;

    private List<String> targetPersonIds;

    private List<String> targetPersonNames;

    private List<String> cameraIds;

    private Integer alertLevel;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSeconds;

    private String description;

    private String policeStationCode;

    private String policeStationName;

    private Long timestamp;
}
