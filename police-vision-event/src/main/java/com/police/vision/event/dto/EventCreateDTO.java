package com.police.vision.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Validated
public class EventCreateDTO implements Serializable {

    @NotBlank(message = "事件名称不能为空")
    private String eventName;

    private String eventType;

    private String eventLevel;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private String organizer;

    private String description;

    private List<Map<String, Double>> areaPolygon;
}
