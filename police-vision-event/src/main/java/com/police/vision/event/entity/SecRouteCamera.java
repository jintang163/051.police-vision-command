package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_route_camera")
public class SecRouteCamera extends BaseEntity {

    @TableField("route_id")
    private Long routeId;

    @TableField("camera_id")
    private Long cameraId;

    @TableField("camera_name")
    private String cameraName;

    @TableField("camera_url")
    private String cameraUrl;

    @TableField("camera_index")
    private Integer cameraIndex;

    @TableField("play_duration")
    private Integer playDuration;
}
