package com.police.vision.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("target_vehicle")
public class TargetVehicle extends BaseEntity {

    private String vehicleId;

    private String plateNo;

    private String vehicleType;

    private Integer controlLevel;

    private Integer status;

    private String remark;
}
