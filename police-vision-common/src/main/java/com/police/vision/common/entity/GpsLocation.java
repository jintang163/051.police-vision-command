package com.police.vision.common.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsLocation implements Serializable {

    private String deviceId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;
    private BigDecimal speed;
    private BigDecimal direction;
    private Integer accuracy;
    private LocalDateTime timestamp;

    public GpsLocation(BigDecimal longitude, BigDecimal latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }
}
