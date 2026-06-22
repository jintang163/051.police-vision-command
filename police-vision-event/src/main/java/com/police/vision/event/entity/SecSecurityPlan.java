package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_security_plan")
public class SecSecurityPlan extends BaseEntity {

    @TableField("event_id")
    private Long eventId;

    @TableField("plan_name")
    private String planName;

    @TableField("plan_type")
    private String planType;

    @TableField("status")
    private Integer status;

    @TableField("plan_template_code")
    private String planTemplateCode;

    @TableField("is_template")
    private Integer isTemplate;

    @TableField("emergency_level")
    private Integer emergencyLevel;

    @TableField("resource_radius")
    private Double resourceRadius;

    @TableField("auto_allocate_resources")
    private Integer autoAllocateResources;

    @TableField("auto_start_video_conference")
    private Integer autoStartVideoConference;

    @TableField("nacos_config_key")
    private String nacosConfigKey;

    @TableField("description")
    private String description;
}
