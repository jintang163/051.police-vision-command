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
public class OfficerEtaResultDTO implements Serializable {

    private Long policeId;

    private String policeName;

    private BigDecimal officerLongitude;

    private BigDecimal officerLatitude;

    private BigDecimal alarmLongitude;

    private BigDecimal alarmLatitude;

    private BigDecimal straightDistance;

    private BigDecimal roadDistance;

    private Integer etaSeconds;

    private Integer trafficLevel;

    private String routePolyline;

    private String strategy;

    private Integer trafficLightsCount;

    private BigDecimal avgSpeed;

    private String roadNames;

    private String routeName;

    private Integer calculateVersion;

    private LocalDateTime calculateTime;

    public String getEtaDisplay() {
        if (etaSeconds == null) return "未知";
        int minutes = etaSeconds / 60;
        int seconds = etaSeconds % 60;
        if (minutes < 60) {
            return minutes + "分" + (seconds > 0 ? seconds + "秒" : "");
        } else {
            int hours = minutes / 60;
            int remainMinutes = minutes % 60;
            return hours + "小时" + (remainMinutes > 0 ? remainMinutes + "分" : "");
        }
    }

    public String getDistanceDisplay() {
        if (roadDistance == null) return straightDistance != null
                ? straightDistance.setScale(2, BigDecimal.ROUND_HALF_UP) + "km(直线)"
                : "未知";
        double km = roadDistance.doubleValue() / 1000.0;
        return String.format("%.2fkm", km);
    }
}
