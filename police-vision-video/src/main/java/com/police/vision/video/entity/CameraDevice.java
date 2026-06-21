package com.police.vision.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("camera_device")
public class CameraDevice extends BaseEntity {

    private String deviceId;

    private String deviceName;

    private String brand;

    private String model;

    private String ipAddress;

    private Integer port;

    private String rtspUrl;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer status;

    private String region;

    private String installLocation;
}
