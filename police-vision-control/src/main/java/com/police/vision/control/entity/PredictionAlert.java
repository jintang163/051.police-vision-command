package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prediction_alert")
public class PredictionAlert extends BaseEntity {

    private String alertId;

    private String alertNo;

    private String alertType;

    private String alertTypeName;

    private Integer alertLevel;

    private String personId;

    private String personName;

    private String personType;

    private Integer controlLevel;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String locationDesc;

    private Double probability;

    private LocalDateTime predictTime;

    private String predictionId;

    private String predictionBatch;

    private String triggerReason;

    private String sensitiveAreaName;

    private String sensitiveAreaType;

    private Integer crowdCount;

    private Integer targetPersonCount;

    private String nearbyPoliceIds;

    private Integer status;

    private String statusName;

    private String handleRemark;

    private Long handleOfficerId;

    private String handleOfficerName;

    private LocalDateTime handleTime;

    private String policeStationCode;

    private String policeStationName;

    private String extraData;
}
