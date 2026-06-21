package com.police.vision.gis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gis_camera_point")
public class CameraPoint extends BaseEntity {

    private String deviceId;

    private String name;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer status;

    private BigDecimal direction;

    private String brand;
}
