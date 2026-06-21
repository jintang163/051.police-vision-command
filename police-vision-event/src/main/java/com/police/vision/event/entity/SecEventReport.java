package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_event_report")
public class SecEventReport extends BaseEntity {

    @TableField("event_id")
    private Long eventId;

    @TableField("report_name")
    private String reportName;

    @TableField("report_url")
    private String reportUrl;

    @TableField("generate_time")
    private LocalDateTime generateTime;

    @TableField("summary")
    private String summary;

    @TableField("pedestrian_count")
    private Long pedestrianCount;

    @TableField("vehicle_count")
    private Long vehicleCount;

    @TableField("alert_count")
    private Integer alertCount;

    @TableField("police_count")
    private Integer policeCount;

    @TableField("camera_count")
    private Integer cameraCount;

    @TableField("post_count")
    private Integer postCount;
}
