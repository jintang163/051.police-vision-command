package com.police.vision.gis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gis_police_location")
public class PoliceLocation extends BaseEntity {

    private Long policeId;

    private String policeNumber;

    private String name;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal speed;

    private BigDecimal direction;

    private String deviceId;

    private Integer status;

    private LocalDateTime timestamp;
}
