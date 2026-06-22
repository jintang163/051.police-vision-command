package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("callback_result")
public class CallbackResult extends BaseEntity {

    private String callbackResultId;

    private String callbackTaskId;

    private String callbackTaskNo;

    private String callId;

    private Integer callDuration;

    private String recordingUrl;

    private String asrFullText;

    private String asrJson;

    private Integer timelinessScore;

    private Integer attitudeScore;

    private Integer solvingScore;

    private Integer overallScore;

    private Integer satisfactionLevel;

    private String satisfactionLevelName;

    private Integer sentimentLabel;

    private String sentimentLabelName;

    private BigDecimal sentimentScore;

    private String sentimentKeywords;

    private String sentimentAnalysis;

    private String complaintKeywords;

    private String praiseKeywords;

    private String suggestionText;

    private String summaryText;

    private Integer autoTransferHuman;

    private String transferReason;

    private Long reviewerId;

    private String reviewerName;

    private Integer reviewStatus;

    private String reviewStatusName;

    private LocalDateTime reviewTime;

    private String reviewRemark;
}
