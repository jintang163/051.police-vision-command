package com.police.vision.common.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YawDetectionResultDTO implements Serializable {

    private Long dispatchId;

    private Long policeId;

    private String dispatchNo;

    private BigDecimal currentLongitude;

    private BigDecimal currentLatitude;

    private BigDecimal expectedLongitude;

    private BigDecimal expectedLatitude;

    private BigDecimal deviationMeters;

    private BigDecimal deviationThreshold;

    private Boolean yaw;

    private String yawReason;

    private Integer yawCount;

    private Boolean needRecalc;

    private Boolean autoRerouted;

    private OfficerEtaResultDTO newEta;

    private LocalDateTime detectTime;

    private String routePolylineSnapshot;
}
