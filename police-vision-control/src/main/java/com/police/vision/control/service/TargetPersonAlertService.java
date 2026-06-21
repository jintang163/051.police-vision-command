package com.police.vision.control.service;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.enums.AlertTypeEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.entity.*;
import com.police.vision.control.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TargetPersonAlertService {

    private final TargetPersonMapper targetPersonMapper;
    private final GeoFenceService geoFenceService;
    private final TargetPersonProfileService profileService;
    private final MqUtil mqUtil;

    private static final ExecutorService ALERT_EXECUTOR = new ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(5000),
            r -> {
                Thread t = new Thread(r, "target-alert-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private static final long SLA_MILLIS = 1000L;
    private static final AtomicLong ALERT_COUNTER = new AtomicLong(0);
    private static final AtomicLong SLA_BREACH_COUNTER = new AtomicLong(0);

    @Transactional(rollbackFor = Exception.class)
    public void processFaceMatchAsync(String personId, String personName, String cameraId,
                                       String cameraName, BigDecimal longitude, BigDecimal latitude,
                                       String snapshotUrl, String videoClipUrl, Float similarity) {
        long start = System.currentTimeMillis();
        ALERT_EXECUTOR.submit(() -> {
            try {
                processFaceMatchInternal(personId, personName, cameraId, cameraName,
                        longitude, latitude, snapshotUrl, videoClipUrl, similarity);
            } catch (Exception e) {
                log.error("处理人脸匹配预警异常：personId={}", personId, e);
            } finally {
                long cost = System.currentTimeMillis() - start;
                ALERT_COUNTER.incrementAndGet();
                if (cost > SLA_MILLIS) {
                    SLA_BREACH_COUNTER.incrementAndGet();
                    log.warn("感知预警SLA告警：personId={}, cost={}ms > 1000ms", personId, cost);
                }
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void processFaceMatchInternal(String personId, String personName, String cameraId,
                                          String cameraName, BigDecimal longitude, BigDecimal latitude,
                                          String snapshotUrl, String videoClipUrl, Float similarity) {
        TargetPerson person = targetPersonMapper.selectByPersonId(personId);
        if (person == null) {
            log.warn("感知预警：人员不存在，跳过：personId={}", personId);
            return;
        }
        if (person.getStatus() == null || person.getStatus() != 1) {
            log.debug("感知预警：人员未在布控状态，跳过：personId={}, status={}", personId, person.getStatus());
            return;
        }
        if (!profileService.canSendFaceAlert(personId)) {
            log.debug("感知预警：未到告警冷却时间，跳过：personId={}", personId);
            return;
        }

        String alertId = "FA" + SnowflakeIdUtil.nextId();
        int alertLevel = person.getControlLevel() != null ? person.getControlLevel() : 2;

        try {
            AlertMessageDTO faceAlert = buildFaceMatchAlert(alertId, person, cameraId, cameraName,
                    longitude, latitude, snapshotUrl, videoClipUrl, similarity, alertLevel);
            mqUtil.send(MqConstant.VIDEO_ALERT_TOPIC + ":" + MqConstant.TAG_ALERT, faceAlert);
            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("alert", faceAlert));
            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("target_alert",
                    Map.of(
                            "alertId", alertId,
                            "person", Map.of(
                                    "personId", person.getPersonId(),
                                    "personName", person.getPersonName(),
                                    "personType", person.getPersonType(),
                                    "personTypeName", person.getPersonTypeName(),
                                    "controlLevel", person.getControlLevel(),
                                    "riskScore", person.getRiskScore()
                            ),
                            "camera", Map.of(
                                    "cameraId", cameraId,
                                    "cameraName", cameraName,
                                    "longitude", longitude,
                                    "latitude", latitude
                            ),
                            "snapshotUrl", snapshotUrl,
                            "similarity", similarity,
                            "alertLevel", alertLevel,
                            "alertTime", LocalDateTime.now().toString()
                    )));

            log.info("【感知即预警】人脸匹配告警：person={}[{}], camera={}, 级别={}, similarity={}%",
                    personName, personId, cameraName, alertLevel, similarity);
        } catch (Exception e) {
            log.error("发送人脸匹配告警失败：personId={}", personId, e);
        }

        try {
            geoFenceService.checkAndTriggerFenceAlert(personId, personName, longitude, latitude,
                    cameraId, cameraName, snapshotUrl, videoClipUrl);
        } catch (Exception e) {
            log.error("围栏检测异常：personId={}", personId, e);
        }

        try {
            incrementPersonStats(personId);
        } catch (Exception e) {
            log.debug("更新人员统计失败：personId={}", personId, e);
        }
    }

    private AlertMessageDTO buildFaceMatchAlert(String alertId, TargetPerson person,
                                                 String cameraId, String cameraName,
                                                 BigDecimal longitude, BigDecimal latitude,
                                                 String snapshotUrl, String videoClipUrl,
                                                 Float similarity, int alertLevel) {
        AlertMessageDTO alert = new AlertMessageDTO();
        alert.setAlertId(alertId);
        alert.setAlertType(AlertTypeEnum.FACE_MATCH.getCode());
        alert.setAlertName(AlertTypeEnum.FACE_MATCH.getName());
        alert.setAlertLevel(alertLevel);
        alert.setCameraId(cameraId);
        alert.setCameraName(cameraName);
        alert.setLongitude(longitude);
        alert.setLatitude(latitude);

        String typeDesc = getPersonTypeDescription(person);
        alert.setDescription(String.format("【感知即预警】%s人员[%s]在[%s]被识别，相似度%.1f%%，风险等级%d级",
                typeDesc, person.getPersonName(), cameraName,
                similarity != null ? similarity : 0f, alertLevel));

        alert.setSnapshotUrl(snapshotUrl);
        alert.setAlertTime(LocalDateTime.now());
        alert.setTargetPersonId(person.getPersonId());
        alert.setTargetPersonName(person.getPersonName());
        alert.setVideoClipUrl(videoClipUrl);

        Map<String, Object> extra = new HashMap<>();
        extra.put("similarity", similarity);
        extra.put("personType", person.getPersonType());
        extra.put("personTypeName", person.getPersonTypeName());
        extra.put("controlLevel", person.getControlLevel());
        extra.put("riskScore", person.getRiskScore());
        extra.put("idCardNo", person.getIdCardNo());
        extra.put("age", person.getAge());
        extra.put("gender", person.getGender());
        extra.put("phone", person.getPhone());
        extra.put("residentAddress", person.getResidentAddress());
        extra.put("criminalTags", person.getCriminalTags());
        extra.put("mentalLevel", person.getMentalLevel());
        extra.put("appealCategory", person.getAppealCategory());
        extra.put("policeStationCode", person.getPoliceStationCode());
        extra.put("policeStationName", person.getPoliceStationName());
        extra.put("processSlaMs", System.currentTimeMillis());
        alert.setExtraData(extra);

        return alert;
    }

    private String getPersonTypeDescription(TargetPerson person) {
        String type = person.getPersonTypeName();
        if (type != null && !type.isEmpty()) return type;
        String pt = person.getPersonType();
        if (pt == null) return "重点";
        switch (pt.toUpperCase()) {
            case "CRIMINAL":
            case "XQ":
                return "前科";
            case "APPEAL":
            case "SF":
                return "上访";
            case "MENTAL":
            case "JS":
                return "精神障碍";
            case "DRUG":
                return "涉毒";
            case "TERROR":
                return "涉恐";
            default:
                return "重点";
        }
    }

    private void incrementPersonStats(String personId) {
        TargetPerson person = targetPersonMapper.selectByPersonId(personId);
        if (person == null) return;
        int count = person.getAlertCount() == null ? 0 : person.getAlertCount();
        person.setAlertCount(count + 1);
        targetPersonMapper.updateById(person);
    }

    public Map<String, Object> getAlertMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalAlerts", ALERT_COUNTER.get());
        metrics.put("slaBreachCount", SLA_BREACH_COUNTER.get());
        metrics.put("slaTargetMs", SLA_MILLIS);
        double rate = ALERT_COUNTER.get() == 0 ? 100.0 :
                100.0 * (ALERT_COUNTER.get() - SLA_BREACH_COUNTER.get()) / ALERT_COUNTER.get();
        metrics.put("slaComplianceRate", Math.round(rate * 100) / 100.0);
        metrics.put("executorQueueSize", ALERT_EXECUTOR != null ?
                ((ThreadPoolExecutor) ALERT_EXECUTOR).getQueue().size() : 0);
        metrics.put("executorActiveCount", ALERT_EXECUTOR != null ?
                ((ThreadPoolExecutor) ALERT_EXECUTOR).getActiveCount() : 0);
        return metrics;
    }
}
