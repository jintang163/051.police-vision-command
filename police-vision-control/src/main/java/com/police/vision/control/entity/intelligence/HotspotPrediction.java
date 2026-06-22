package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hotspot_prediction")
public class HotspotPrediction extends BaseEntity {

    private String predictionId;

    private String predictionNo;

    private String predictionBatch;

    private LocalDateTime predictStartTime;

    private LocalDateTime predictEndTime;

    private Integer predictHours;

    private String areaCode;

    private String areaName;

    private String gridCode;

    private BigDecimal gridCenterLng;

    private BigDecimal gridCenterLat;

    private String caseType;

    private String caseTypeName;

    private Integer predictedCount;

    private BigDecimal probability;

    private BigDecimal riskScore;

    private Integer riskLevel;

    private String riskLevelName;

    private Integer historicalCount;

    private BigDecimal trendRate;

    private String seasonalPattern;

    private String contributingFactors;

    private String preventionSuggestion;

    private Integer actualCount;

    private BigDecimal predictionAccuracy;

    private Integer status;

    private String statusName;

    private LocalDateTime modelRunTime;

    private String modelVersion;

    private String sarimaParams;

    private String policeStationCode;

    private String policeStationName;
}
