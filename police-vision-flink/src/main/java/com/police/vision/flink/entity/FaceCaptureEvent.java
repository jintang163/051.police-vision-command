package com.police.vision.flink.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaceCaptureEvent implements Serializable {

    private String captureId;

    private String personId;

    private String personName;

    private String personType;

    private Integer controlLevel;

    private Boolean isTargetPerson;

    private String cameraId;

    private String cameraName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String gridCode;

    private String areaCode;

    private Float similarity;

    private LocalDateTime eventTime;

    private Long timestamp;

    public long getEventTimestamp() {
        return timestamp != null ? timestamp : System.currentTimeMillis();
    }

    public String getAreaKey() {
        if (areaCode != null && !areaCode.isEmpty()) return areaCode;
        if (gridCode != null && !gridCode.isEmpty()) return gridCode;
        if (cameraId != null) return "CAM:" + cameraId;
        return "DEFAULT";
    }

    public double getLongitudeDouble() {
        return longitude != null ? longitude.doubleValue() : 0.0;
    }

    public double getLatitudeDouble() {
        return latitude != null ? latitude.doubleValue() : 0.0;
    }
}
