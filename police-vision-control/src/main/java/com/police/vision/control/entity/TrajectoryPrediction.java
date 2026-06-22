package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trajectory_prediction")
public class TrajectoryPrediction extends BaseEntity {

    private String predictionId;

    private String personId;

    private String personName;

    private String predictionBatch;

    private Integer predictionRank;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String locationDesc;

    private Double probability;

    private Integer predictMinutesAhead;

    private LocalDateTime predictTime;

    private LocalDateTime predictWindowStart;

    private LocalDateTime predictWindowEnd;

    private String areaCode;

    private String areaName;

    private Integer isSensitiveArea;

    private String sensitiveAreaType;

    private Integer crowdRiskLevel;

    private String modelVersion;

    private Double confidence;

    private String featureSnapshot;

    private Integer status;
}
