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
public class FlinkAlertEvent implements Serializable {

    private String alertId;
    private Integer alertType;
    private Integer alertLevel;
    private String cameraId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String gridCode;
    private LocalDateTime eventTime;
    private Long timestamp;

    public long getEventTimestamp() {
        return timestamp != null ? timestamp : System.currentTimeMillis();
    }
}
