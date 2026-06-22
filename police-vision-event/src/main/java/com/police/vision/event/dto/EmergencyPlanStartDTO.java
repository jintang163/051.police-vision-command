package com.police.vision.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmergencyPlanStartDTO {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    private Long planId;

    private String templateCode;

    private Double resourceRadius;

    private Boolean autoAllocateResources;

    private Boolean autoStartVideoConference;

    private String operatorName;

    private Long operatorId;
}
