package com.police.vision.alarm.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DispatchContext {

    private Long alarmId;

    private Integer alarmType;

    private Integer priority;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private List<PoliceOfficer> availableOfficers;

    private List<PoliceOfficer> recommendedOfficers = new ArrayList<>();

    private boolean needFireTruck;

    private boolean needNotifyStation;

    private String dispatchSuggestion;

    private int requiredOfficerCount;

    private double maxDistance;
}
