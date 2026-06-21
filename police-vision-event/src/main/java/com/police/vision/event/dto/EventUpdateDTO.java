package com.police.vision.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Validated
public class EventUpdateDTO implements Serializable {

    @NotNull(message = "事件ID不能为空")
    private Long id;

    private String eventName;

    private String eventType;

    private String eventLevel;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String organizer;

    private String description;

    private List<Map<String, Double>> areaPolygon;
}
