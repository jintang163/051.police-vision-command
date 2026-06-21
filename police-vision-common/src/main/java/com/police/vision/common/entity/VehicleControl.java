package com.police.vision.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vehicle_control")
public class VehicleControl extends BaseEntity {

    private String controlNo;

    private String controlName;

    private Integer controlType;

    private Integer controlLevel;

    private String plateNo;

    private String plateColor;

    private String vehicleType;

    private String vehicleColor;

    private String vehicleBrand;

    private String areaCode;

    private String areaName;

    private String crossingIds;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String timeRules;

    private String controlReason;

    private Integer status;

    private Integer warningCount;

    private LocalDateTime lastWarningTime;

    private String description;

    private Long createDeptId;

    private Long createUserId;

    private Long approveUserId;

    private LocalDateTime approveTime;
}
