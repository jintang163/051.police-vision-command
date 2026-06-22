package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_emergency_fence")
public class SecEmergencyFence extends BaseEntity {

    @TableField("event_id")
    private Long eventId;

    @TableField("fence_name")
    private String fenceName;

    @TableField("fence_type")
    private String fenceType;

    @TableField("fence_geometry")
    private String fenceGeometry;

    @TableField("center_lng")
    private Double centerLng;

    @TableField("center_lat")
    private Double centerLat;

    @TableField("radius_meters")
    private Double radiusMeters;

    @TableField("fill_color")
    private String fillColor;

    @TableField("stroke_color")
    private String strokeColor;

    @TableField("stroke_weight")
    private Integer strokeWeight;

    @TableField("opacity")
    private Double opacity;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("creator_name")
    private String creatorName;

    @TableField("status")
    private Integer status;

    @TableField("description")
    private String description;
}
