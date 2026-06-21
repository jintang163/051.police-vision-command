package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_traffic_alert")
public class SecTrafficAlert extends BaseEntity {

    @TableField("event_id")
    private Long eventId;

    @TableField("alert_type")
    private String alertType;

    @TableField("alert_level")
    private Integer alertLevel;

    @TableField("location")
    private String location;

    @TableField("lng")
    private Double lng;

    @TableField("lat")
    private Double lat;

    @TableField("count_value")
    private Long countValue;

    @TableField("threshold_value")
    private Long thresholdValue;

    @TableField("alert_time")
    private LocalDateTime alertTime;

    @TableField("handled")
    private Integer handled;

    @TableField("handle_remark")
    private String handleRemark;
}
