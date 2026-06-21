package com.police.vision.common.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiDispatchPlanDTO implements Serializable {

    private Long alarmId;

    private BigDecimal alarmLongitude;

    private BigDecimal alarmLatitude;

    private String alarmAddress;

    private BigDecimal rendezvousLongitude;

    private BigDecimal rendezvousLatitude;

    private String rendezvousName;

    private String rendezvousAddress;

    private Integer rendezvousType;

    private BigDecimal rendezvousToAlarmDistance;

    private Integer rendezvousToAlarmEta;

    private Integer totalPoliceCount;

    private Integer estimatedArrivalSeconds;

    private List<OfficerEtaResultDTO> officerEtaList;

    private Map<String, Object> rendezvousExtra;

    private String planDescription;

    private String snapshotId;

    public String getEstimatedArrivalDisplay() {
        if (estimatedArrivalSeconds == null) return "未知";
        int minutes = estimatedArrivalSeconds / 60;
        if (minutes < 60) return minutes + "分钟";
        return (minutes / 60) + "小时" + (minutes % 60) + "分";
    }
}
