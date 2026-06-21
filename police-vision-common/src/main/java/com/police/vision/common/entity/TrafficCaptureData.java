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
public class TrafficCaptureData implements Serializable {

    private String captureId;

    private String crossingId;

    private String crossingName;

    private String cameraId;

    private String cameraName;

    private String laneNo;

    private String plateNo;

    private String plateColor;

    private String vehicleType;

    private String vehicleColor;

    private String vehicleBrand;

    private String vehicleModel;

    private BigDecimal speed;

    private Integer direction;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String imageUrl;

    private String plateImageUrl;

    private LocalDateTime captureTime;

    private String dataSource;

    private Integer isTarget;

    private BigDecimal confidence;

    public long getEventTimestamp() {
        return captureTime != null ? java.sql.Timestamp.valueOf(captureTime).getTime() : System.currentTimeMillis();
    }
}
