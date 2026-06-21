package com.police.vision.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dispatch_traffic_snapshot")
public class DispatchTrafficSnapshot extends BaseEntity {

    private String snapshotId;

    private Long dispatchId;

    private String dispatchNo;

    private Long alarmId;

    private String snapshotType;

    private BigDecimal alarmLongitude;

    private BigDecimal alarmLatitude;

    private String alarmAddress;

    private String policeIdsStr;

    private Integer policeCount;

    private String officerEtaData;

    private String trafficStatusData;

    private String trafficData;

    private String routePolylineData;

    private String multiDispatchPlanData;

    private BigDecimal avgTrafficLevel;

    private BigDecimal totalRoadDistance;

    private Integer avgEtaSeconds;

    private Integer fastestEtaSeconds;

    private Long fastestPoliceId;

    private String fastestPoliceName;

    private BigDecimal rendezvousLongitude;

    private BigDecimal rendezvousLatitude;

    private String rendezvousName;

    private Integer rendezvousEtaSeconds;

    private String weatherInfo;

    private String weatherData;

    private String source;

    private String remark;

    private LocalDateTime snapshotTime;
}
