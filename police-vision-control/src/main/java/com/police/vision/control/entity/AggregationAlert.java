package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aggregation_alert")
public class AggregationAlert extends BaseEntity {

    private String alertId;

    private String alertNo;

    private String areaCode;

    private String areaName;

    private BigDecimal centerLongitude;

    private BigDecimal centerLatitude;

    private Integer personCount;

    private String personIds;

    private String personNames;

    private Integer targetPersonCount;

    private String targetPersonIds;

    private String targetPersonNames;

    private Integer alertLevel;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSeconds;

    private String cameraIds;

    private String snapshotUrls;

    private Integer status;

    private String statusName;

    private String description;

    private String handleRemark;

    private Long handleOfficerId;

    private LocalDateTime handleTime;

    private String policeStationCode;

    private String policeStationName;
}
