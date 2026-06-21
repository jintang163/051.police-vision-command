package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_event")
public class SecEvent extends BaseEntity {

    @TableField("event_name")
    private String eventName;

    @TableField("event_type")
    private String eventType;

    @TableField("event_level")
    private String eventLevel;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("organizer")
    private String organizer;

    @TableField("description")
    private String description;

    @TableField("area_polygon")
    private String areaPolygon;

    @TableField("status")
    private Integer status;
}
