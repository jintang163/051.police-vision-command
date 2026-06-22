package com.police.vision.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmergencyResourceQueryDTO {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    private Double lng;

    private Double lat;

    private Double radiusMeters;

    private String resourceType;
}
