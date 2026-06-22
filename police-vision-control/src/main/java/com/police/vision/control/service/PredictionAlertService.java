package com.police.vision.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.util.MqUtil;
import com.police.vision.control.entity.PredictionAlert;
import com.police.vision.control.entity.TargetPerson;
import com.police.vision.control.entity.TrajectoryPrediction;
import com.police.vision.control.mapper.PredictionAlertMapper;
import com.police.vision.control.mapper.TargetPersonMapper;
import com.police.vision.control.mapper.TrajectoryPredictionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionAlertService {

    private final PredictionAlertMapper alertMapper;
    private final TrajectoryPredictionMapper predictionMapper;
    private final TargetPersonMapper targetPersonMapper;
    private final GeoFenceService geoFenceService;
    private final MqUtil mqUtil;

    private static final String ALERT_TYPE_SENSITIVE = "SENSITIVE_AREA";
    private static final String ALERT_TYPE_CROWD = "CROWD_GATHERING";
    private static final String ALERT_TYPE_BOTH = "SENSITIVE_CROWD";

    @Transactional(rollbackFor = Exception.class)
    public List<PredictionAlert> generateAlertsFromPredictions(String predictionBatch) {
        List<TrajectoryPrediction> predictions = predictionMapper.selectByBatch(predictionBatch);
        if (predictions == null || predictions.isEmpty()) {
            return Collections.emptyList();
        }
        List<PredictionAlert> alerts = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        for (TrajectoryPrediction pred : predictions) {
            boolean sensitive = pred.getIsSensitiveArea() != null && pred.getIsSensitiveArea() == 1;
            boolean crowd = pred.getCrowdRiskLevel() != null && pred.getCrowdRiskLevel() >= 2;
            boolean highProb = pred.getProbability() != null && pred.getProbability() >= 0.2;

            if (!highProb || (!sensitive && !crowd)) continue;

            String dedupKey = pred.getPersonId() + "_" + pred.getPredictionBatch();
            if (processed.contains(dedupKey)) continue;
            processed.add(dedupKey);

            String alertType;
            String alertTypeName;
            int alertLevel;
            String reason;

            if (sensitive && crowd) {
                alertType = ALERT_TYPE_BOTH;
                alertTypeName = "敏感区+多人聚集";
                alertLevel = Math.max(pred.getCrowdRiskLevel(), 3);
                reason = String.format("预测该人员%.0f%%概率出现在敏感区域【%s】，且该区域预计人员聚集风险等级%d级",
                        pred.getProbability() * 100,
                        pred.getSensitiveAreaType() != null ? pred.getSensitiveAreaType() : "未知",
                        pred.getCrowdRiskLevel());
            } else if (sensitive) {
                alertType = ALERT_TYPE_SENSITIVE;
                alertTypeName = "敏感区域预警";
                alertLevel = 2;
                reason = String.format("预测该人员%.0f%%概率在未来30分钟内进入敏感区域【%s】",
                        pred.getProbability() * 100,
                        pred.getSensitiveAreaType() != null ? pred.getSensitiveAreaType() : "未知");
            } else {
                alertType = ALERT_TYPE_CROWD;
                alertTypeName = "多人聚集预警";
                alertLevel = pred.getCrowdRiskLevel();
                reason = String.format("预测该人员%.0f%%概率出现在聚集风险等级%d级区域",
                        pred.getProbability() * 100, pred.getCrowdRiskLevel());
            }

            TargetPerson person = targetPersonMapper.selectByPersonId(pred.getPersonId());
            PredictionAlert alert = buildAlert(pred, person, alertType, alertTypeName, alertLevel, reason);
            alertMapper.insert(alert);
            alerts.add(alert);

            pushAlertToPolice(alert);
        }
        return alerts;
    }

    private PredictionAlert buildAlert(TrajectoryPrediction pred, TargetPerson person,
                                       String alertType, String alertTypeName, int alertLevel,
                                       String reason) {
        PredictionAlert alert = new PredictionAlert();
        alert.setAlertId(UUID.randomUUID().toString().replace("-", ""));
        alert.setAlertNo("PA" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000)));
        alert.setAlertType(alertType);
        alert.setAlertTypeName(alertTypeName);
        alert.setAlertLevel(alertLevel);
        alert.setPersonId(pred.getPersonId());
        alert.setPersonName(pred.getPersonName());
        alert.setPersonType(person != null ? person.getPersonType() : "OTHER");
        alert.setControlLevel(person != null ? person.getControlLevel() : 1);
        alert.setLongitude(pred.getLongitude());
        alert.setLatitude(pred.getLatitude());
        alert.setLocationDesc(pred.getLocationDesc());
        alert.setProbability(pred.getProbability());
        alert.setPredictTime(pred.getPredictTime());
        alert.setPredictionId(pred.getPredictionId());
        alert.setPredictionBatch(pred.getPredictionBatch());
        alert.setTriggerReason(reason);
        alert.setSensitiveAreaName(pred.getLocationDesc());
        alert.setSensitiveAreaType(pred.getSensitiveAreaType());
        alert.setCrowdCount(pred.getCrowdRiskLevel() != null ? pred.getCrowdRiskLevel() * 5 : 0);
        alert.setTargetPersonCount(1);
        alert.setStatus(0);
        alert.setStatusName("待处理");
        alert.setPoliceStationCode(person != null ? person.getPoliceStationCode() : null);
        alert.setPoliceStationName(person != null ? person.getPoliceStationName() : null);
        return alert;
    }

    private void pushAlertToPolice(PredictionAlert alert) {
        try {
            Map<String, Object> pushMsg = new LinkedHashMap<>();
            pushMsg.put("alertId", alert.getAlertId());
            pushMsg.put("alertNo", alert.getAlertNo());
            pushMsg.put("alertType", alert.getAlertType());
            pushMsg.put("alertTypeName", alert.getAlertTypeName());
            pushMsg.put("alertLevel", alert.getAlertLevel());
            pushMsg.put("personId", alert.getPersonId());
            pushMsg.put("personName", alert.getPersonName());
            pushMsg.put("longitude", alert.getLongitude());
            pushMsg.put("latitude", alert.getLatitude());
            pushMsg.put("locationDesc", alert.getLocationDesc());
            pushMsg.put("probability", alert.getProbability());
            pushMsg.put("predictTime", alert.getPredictTime());
            pushMsg.put("triggerReason", alert.getTriggerReason());
            pushMsg.put("policeStationCode", alert.getPoliceStationCode());
            pushMsg.put("timestamp", System.currentTimeMillis());

            mqUtil.send(MqConstant.CONTROL_TOPIC + ":" + MqConstant.TAG_ALARM, pushMsg);

            Map<String, Object> wsMsg = new LinkedHashMap<>();
            wsMsg.put("type", "PREDICTION_ALERT");
            wsMsg.put("data", pushMsg);
            mqUtil.send(MqConstant.WEBSOCKET_PUSH_TOPIC + ":prediction_alert", wsMsg);

            log.info("轨迹预测预警推送完成：alertId={}, alertType={}, person={}",
                    alert.getAlertId(), alert.getAlertType(), alert.getPersonName());
        } catch (Exception e) {
            log.error("轨迹预测预警推送失败：alertId={}", alert.getAlertId(), e);
        }
    }

    public List<PredictionAlert> listAlerts(Integer status, String personId, Integer alertLevel,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             int pageNum, int pageSize) {
        LambdaQueryWrapper<PredictionAlert> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(PredictionAlert::getStatus, status);
        if (personId != null && !personId.isEmpty()) wrapper.eq(PredictionAlert::getPersonId, personId);
        if (alertLevel != null) wrapper.ge(PredictionAlert::getAlertLevel, alertLevel);
        if (startTime != null && endTime != null) {
            wrapper.between(PredictionAlert::getCreateTime, startTime, endTime);
        }
        wrapper.orderByDesc(PredictionAlert::getAlertLevel, PredictionAlert::getCreateTime);
        wrapper.last("limit " + (pageNum - 1) * pageSize + "," + pageSize);
        return alertMapper.selectList(wrapper);
    }

    public long countAlerts(Integer status, String personId, Integer alertLevel,
                            LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<PredictionAlert> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(PredictionAlert::getStatus, status);
        if (personId != null && !personId.isEmpty()) wrapper.eq(PredictionAlert::getPersonId, personId);
        if (alertLevel != null) wrapper.ge(PredictionAlert::getAlertLevel, alertLevel);
        if (startTime != null && endTime != null) {
            wrapper.between(PredictionAlert::getCreateTime, startTime, endTime);
        }
        return alertMapper.selectCount(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public PredictionAlert handleAlert(String alertId, Integer targetStatus,
                                       String statusName, String remark,
                                       Long officerId, String officerName) {
        LambdaQueryWrapper<PredictionAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PredictionAlert::getAlertId, alertId);
        PredictionAlert alert = alertMapper.selectOne(wrapper);
        if (alert == null) {
            throw new IllegalArgumentException("预警不存在：" + alertId);
        }
        alert.setStatus(targetStatus != null ? targetStatus : 2);
        alert.setStatusName(statusName != null ? statusName : "已处理");
        alert.setHandleRemark(remark);
        alert.setHandleOfficerId(officerId);
        alert.setHandleOfficerName(officerName);
        alert.setHandleTime(LocalDateTime.now());
        alertMapper.updateById(alert);
        log.info("轨迹预测预警已处理：alertId={}, officer={}", alertId, officerName);
        return alert;
    }

    public Map<String, Object> getAlertStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

        long todayTotal = countAlerts(null, null, null, todayStart, now);
        long pending = countAlerts(0, null, null, todayStart, now);
        long processing = countAlerts(1, null, null, todayStart, now);
        long handled = countAlerts(2, null, null, todayStart, now);
        long highRisk = countAlerts(null, null, 3, todayStart, now);

        stats.put("todayTotal", todayTotal);
        stats.put("pendingCount", pending);
        stats.put("processingCount", processing);
        stats.put("handledCount", handled);
        stats.put("highRiskCount", highRisk);

        Map<String, Long> typeDist = new LinkedHashMap<>();
        typeDist.put("SENSITIVE_AREA", 0L);
        typeDist.put("CROWD_GATHERING", 0L);
        typeDist.put("SENSITIVE_CROWD", 0L);
        List<PredictionAlert> todayAlerts = listAlerts(null, null, null, todayStart, now, 1, 1000);
        for (PredictionAlert a : todayAlerts) {
            typeDist.merge(a.getAlertType(), 1L, Long::sum);
        }
        stats.put("typeDistribution", typeDist);
        return stats;
    }

    public Map<String, Object> autoDispatchPolice(String alertId) {
        LambdaQueryWrapper<PredictionAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PredictionAlert::getAlertId, alertId);
        PredictionAlert alert = alertMapper.selectOne(wrapper);
        if (alert == null) {
            throw new IllegalArgumentException("预警不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alertId", alertId);
        result.put("dispatched", false);
        result.put("message", "无可用警力");

        try {
            Map<String, Object> dispatchMsg = new LinkedHashMap<>();
            dispatchMsg.put("dispatchType", "PREDICTION_ALERT");
            dispatchMsg.put("alertId", alertId);
            dispatchMsg.put("alertLevel", alert.getAlertLevel());
            dispatchMsg.put("longitude", alert.getLongitude());
            dispatchMsg.put("latitude", alert.getLatitude());
            dispatchMsg.put("locationDesc", alert.getLocationDesc());
            dispatchMsg.put("personId", alert.getPersonId());
            dispatchMsg.put("personName", alert.getPersonName());
            dispatchMsg.put("triggerReason", alert.getTriggerReason());
            dispatchMsg.put("policeStationCode", alert.getPoliceStationCode());
            dispatchMsg.put("priority", Math.min(5, alert.getAlertLevel() + 1));

            mqUtil.send(MqConstant.DISPATCH_TOPIC + ":" + MqConstant.TAG_AUTO_DISPATCH, dispatchMsg);

            alert.setStatus(1);
            alert.setStatusName("已派警");
            alertMapper.updateById(alert);

            result.put("dispatched", true);
            result.put("message", "已自动派警至辖区派出所");
            result.put("policeStationCode", alert.getPoliceStationCode());
        } catch (Exception e) {
            log.error("自动派警失败：alertId={}", alertId, e);
            result.put("message", "派警失败：" + e.getMessage());
        }
        return result;
    }
}
