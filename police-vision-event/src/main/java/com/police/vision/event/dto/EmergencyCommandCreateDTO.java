package com.police.vision.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class EmergencyCommandCreateDTO {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    private Long planId;

    @NotBlank(message = "指令标题不能为空")
    private String commandTitle;

    @NotBlank(message = "指令内容不能为空")
    private String commandContent;

    private Integer priority;

    private Integer deadlineMinutes;

    private String senderName;

    private Long senderId;

    private List<Long> receiverDeptIds;

    private List<String> receiverNames;

    private String remark;
}
