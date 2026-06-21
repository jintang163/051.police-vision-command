package com.police.vision.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Validated
public class RouteCreateDTO implements Serializable {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    @NotBlank(message = "路线名称不能为空")
    private String routeName;

    private String startPoint;

    private String endPoint;

    private List<Map<String, Double>> waypoints;

    private List<Long> cameraIds;
}
