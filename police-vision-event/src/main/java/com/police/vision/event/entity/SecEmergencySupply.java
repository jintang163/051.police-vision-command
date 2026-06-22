package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_emergency_supply")
public class SecEmergencySupply extends BaseEntity {

    @TableField("event_id")
    private Long eventId;

    @TableField("supply_name")
    private String supplyName;

    @TableField("supply_type")
    private String supplyType;

    @TableField("quantity")
    private Integer quantity;

    @TableField("unit")
    private String unit;

    @TableField("lng")
    private Double lng;

    @TableField("lat")
    private Double lat;

    @TableField("address")
    private String address;

    @TableField("contact_person")
    private String contactPerson;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("status")
    private Integer status;

    @TableField("distance_meters")
    private Double distanceMeters;

    @TableField("description")
    private String description;
}
