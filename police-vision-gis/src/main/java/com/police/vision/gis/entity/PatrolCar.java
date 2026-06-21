package com.police.vision.gis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gis_patrol_car")
public class PatrolCar extends BaseEntity {

    private String plateNumber;

    private Long policeId;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal speed;

    private Integer status;
}
