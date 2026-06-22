package com.police.vision.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmergencyFenceCreateDTO {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    @NotBlank(message = "封控区名称不能为空")
    private String fenceName;

    private String fenceType;

    @NotBlank(message = "围栏几何数据不能为空")
    private String fenceGeometry;

    private Double centerLng;

    private Double centerLat;

    private Double radiusMeters;

    private String fillColor;

    private String strokeColor;

    private Integer strokeWeight;

    private Double opacity;

    private Integer sortOrder;

    private Long creatorId;

    private String creatorName;

    private String description;
}
