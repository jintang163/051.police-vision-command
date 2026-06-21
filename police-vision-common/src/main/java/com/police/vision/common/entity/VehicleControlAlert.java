package com.police.vision.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vehicle_control_alert")
public class VehicleControlAlert extends BaseEntity {

    private String alertNo;

    private String controlId;

    private String controlNo;

    private String controlName;

    private Integer controlLevel;

    private Integer alertType;

    private String alertName;

    private Integer alertLevel;

    private String plateNo;

    private String plateColor;

    private String vehicleType;

    private String vehicleColor;

    private String vehicleBrand;

    private String crossingId;

    private String crossingName;

    private String cameraId;

    private String cameraName;

    private String laneNo;

    private BigDecimal speed;

    private Integer direction;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String imageUrl;

    private String plateImageUrl;

    private LocalDateTime captureTime;

    private LocalDateTime alertTime;

    private String description;

    private Integer status;

    private Long handlerId;

    private String handlerName;

    private LocalDateTime handleTime;

    private String handleResult;

    private String handleRemark;

    private Integer pushStatus;

    private LocalDateTime pushTime;
}
