package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_task_group")
public class SecTaskGroup extends BaseEntity {

    @TableField("plan_id")
    private Long planId;

    @TableField("group_name")
    private String groupName;

    @TableField("group_leader")
    private String groupLeader;

    @TableField("group_leader_id")
    private Long groupLeaderId;

    @TableField("description")
    private String description;
}
