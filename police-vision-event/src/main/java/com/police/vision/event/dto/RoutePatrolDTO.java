package com.police.vision.event.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RoutePatrolDTO implements Serializable {

    private Long routeId;

    private Long eventId;

    private String patrolTaskId;

    private List<RouteCameraDTO> cameras;

    private String status;

    private Long startTime;
}
