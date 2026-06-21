package com.police.vision.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;

@Data
@Validated
public class TrafficMonitorDTO implements Serializable {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    private String alertType;

    private Long pedestrianThreshold;

    private Long vehicleThreshold;

    private Integer windowSeconds;
}
