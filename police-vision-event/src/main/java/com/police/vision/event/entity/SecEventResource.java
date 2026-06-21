package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_event_resource")
public class SecEventResource extends BaseEntity {

    @TableField("event_id")
    private Long eventId;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("resource_name")
    private String resourceName;

    @TableField("lng")
    private Double lng;

    @TableField("lat")
    private Double lat;

    @TableField("distance")
    private Double distance;
}
