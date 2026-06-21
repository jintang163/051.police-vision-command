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
}
