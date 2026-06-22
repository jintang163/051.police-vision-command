package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("callback_task")
public class CallbackTask extends BaseEntity {

    private String callbackTaskId;

    private String callbackTaskNo;

    private Integer sourceType;

    private String sourceTypeName;

    private String sourceId;

    private String sourceNo;

    private String caseType;

    private String caseTypeName;

    private String briefDescription;

    private Long alertOfficerId;

    private String alertOfficerName;

    private String alertDeptCode;

    private String alertDeptName;

    private String reporterName;

    private String reporterPhone;

    private String reporterIdCard;

    private LocalDateTime reportTime;

    private LocalDateTime closeTime;

    private String closeDeptCode;

    private String closeDeptName;

    private LocalDateTime scheduledTime;

    private String templateId;

    private String templateName;

    private Integer priority;

    private Integer taskStatus;

    private String taskStatusName;

    private String callId;

    private LocalDateTime callStartTime;

    private LocalDateTime callEndTime;

    private Integer callDuration;

    private String callResult;

    private String callResultMsg;

    private Integer callTimes;

    private LocalDateTime lastAttemptTime;

    private LocalDateTime nextAttemptTime;

    private Integer maxRetryTimes;

    private Integer transferHumanFlag;

    private String transferHumanReason;

    private LocalDateTime transferHumanTime;

    private Long humanOfficerId;

    private String humanOfficerName;

    private LocalDateTime humanFinishTime;

    private String areaCode;

    private String areaName;

    private String remark;
}
