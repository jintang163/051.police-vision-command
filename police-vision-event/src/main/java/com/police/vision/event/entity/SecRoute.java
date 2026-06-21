package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_route")
public class SecRoute extends BaseEntity {

    @TableField("event_id")
    private Long eventId;

    @TableField("route_name")
    private String routeName;

    @TableField("start_point")
    private String startPoint;

    @TableField("end_point")
    private String endPoint;

    @TableField("waypoints")
    private String waypoints;

    @TableField("status")
    private Integer status;
}
