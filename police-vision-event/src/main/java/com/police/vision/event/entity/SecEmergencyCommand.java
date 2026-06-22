package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_emergency_command")
public class SecEmergencyCommand extends BaseEntity {

    @TableField("command_no")
    private String commandNo;

    @TableField("event_id")
    private Long eventId;

    @TableField("plan_id")
    private Long planId;

    @TableField("command_title")
    private String commandTitle;

    @TableField("command_content")
    private String commandContent;

    @TableField("priority")
    private Integer priority;

    @TableField("status")
    private Integer status;

    @TableField("sender_id")
    private Long senderId;

    @TableField("sender_name")
    private String senderName;

    @TableField("receiver_dept_ids")
    private String receiverDeptIds;

    @TableField("receiver_names")
    private String receiverNames;

    @TableField("dispatch_time")
    private LocalDateTime dispatchTime;

    @TableField("receive_time")
    private LocalDateTime receiveTime;

    @TableField("execute_start_time")
    private LocalDateTime executeStartTime;

    @TableField("feedback_time")
    private LocalDateTime feedbackTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;

    @TableField("feedback_content")
    private String feedbackContent;

    @TableField("feedback_attachments")
    private String feedbackAttachments;

    @TableField("deadline_minutes")
    private Integer deadlineMinutes;

    @TableField("timeout_count")
    private Integer timeoutCount;

    @TableField("remark")
    private String remark;

    @TableField("parent_command_id")
    private Long parentCommandId;
}
