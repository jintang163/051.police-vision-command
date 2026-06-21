package com.police.vision.alarm.entity;

import com.police.vision.common.dto.MultiDispatchPlanDTO;
import com.police.vision.common.dto.OfficerEtaResultDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DispatchContext {

    private Long alarmId;

    private Integer alarmType;

    private Integer priority;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String alarmAddress;

    private String alarmContent;

    private List<PoliceOfficer> availableOfficers;

    private List<PoliceOfficer> recommendedOfficers = new ArrayList<>();

    private boolean needFireTruck;

    private boolean needNotifyStation;

    private String dispatchSuggestion;

    private int requiredOfficerCount;

    private double maxDistance;

    private String dispatchMode;

    private boolean useSmartEta;

    private Map<Long, OfficerEtaResultDTO> officerEtaMap = new HashMap<>();

    private String trafficSnapshotId;

    private BigDecimal avgTrafficLevel;

    private Integer fastestEtaSeconds;

    private Long fastestPoliceId;

    private MultiDispatchPlanDTO multiDispatchPlan;

    private String dispatchAlgorithm;

    private String dispatchVersion;

    private BigDecimal savedEtaPercent;

    private AmapTrafficStatusDTO rawTrafficData;

    private LocalDateTime calculateTime;

    private String remark;
}
