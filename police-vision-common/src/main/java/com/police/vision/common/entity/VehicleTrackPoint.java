package com.police.vision.common.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTrackPoint implements Serializable {

    private String trackId;

    private String plateNo;

    private String vehicleType;

    private String vehicleColor;

    private String crossingId;

    private String crossingName;

    private String cameraId;

    private String cameraName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal speed;

    private Integer direction;

    private String laneNo;

    private String imageUrl;

    private LocalDateTime captureTime;

    private Long timestamp;
}
