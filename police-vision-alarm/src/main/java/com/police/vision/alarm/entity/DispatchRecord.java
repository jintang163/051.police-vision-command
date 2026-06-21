package com.police.vision.alarm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dispatch_record")
public class DispatchRecord extends BaseEntity {

    private String dispatchNo;

    private Long alarmId;

    @TableField(exist = false)
    private List<Long> policeIds;

    private String policeIdsStr;

    private Long commanderId;

    private LocalDateTime dispatchTime;

    private LocalDateTime responseTime;

    private Integer dispatchStatus;

    private String dispatchRemark;

    private Integer priority;

    private String dispatchAlgorithm;

    private String dispatchMode;

    private String trafficSnapshotId;

    private Integer fastestEtaSeconds;

    private Long fastestPoliceId;

    private String fastestPoliceName;

    private BigDecimal avgTrafficLevel;

    private BigDecimal totalRoadDistance;

    private String officerEtaSnapshot;

    private String routePolylineSnapshot;

    private BigDecimal rendezvousLongitude;

    private BigDecimal rendezvousLatitude;

    private String rendezvousName;

    private Integer rendezvousEtaSeconds;

    private String multiDispatchPlanData;

    private Integer yawRecalcCount;

    private String lastRecalcReason;

    private LocalDateTime lastRecalcTime;

    private String dispatchVersion;

    private BigDecimal savedEtaPercent;
}
