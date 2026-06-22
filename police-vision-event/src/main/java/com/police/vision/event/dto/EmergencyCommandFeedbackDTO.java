package com.police.vision.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmergencyCommandFeedbackDTO {

    @NotNull(message = "指令ID不能为空")
    private Long commandId;

    @NotNull(message = "反馈人ID不能为空")
    private Long operatorId;

    private String operatorName;

    private String operatorDept;

    private String feedbackContent;

    private String feedbackAttachments;

    private Integer toStatus;

    private String operateRemark;
}
