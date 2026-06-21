package com.police.vision.common.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessageDTO implements Serializable {

    private String alertId;
    private Integer alertType;
    private String alertName;
    private Integer alertLevel;
    private String cameraId;
    private String cameraName;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String description;
    private Map<String, Object> extraData;
    private String snapshotUrl;
    private String videoClipUrl;
    private LocalDateTime alertTime;
    private String targetPersonId;
    private String targetPersonName;
    private String targetPlateNo;
}
