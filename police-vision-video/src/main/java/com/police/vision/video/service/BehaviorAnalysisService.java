package com.police.vision.video.service;

import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.enums.AlertTypeEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.video.entity.AlertRecord;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.mapper.AlertRecordMapper;
import com.police.vision.video.mapper.CameraDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAnalysisService {

    private final AlertRecordMapper alertRecordMapper;
    private final CameraDeviceMapper cameraDeviceMapper;
    private final MqUtil mqUtil;
    private final VideoStorageService videoStorageService;

    public Map<String, Object> analyzeBehavior(String streamUrl, String cameraId) {
        try {
            log.info("开始行为分析：streamUrl={}, cameraId={}", streamUrl, cameraId);
            Map<String, Object> result = new HashMap<>();
            AlertTypeEnum[] behaviorTypes = {
                    AlertTypeEnum.FIGHT_DETECTED,
                    AlertTypeEnum.CROWD_GATHERING,
                    AlertTypeEnum.FALL_DETECTED,
                    AlertTypeEnum.TRESSPASSING,
                    AlertTypeEnum.ABNORMAL_BEHAVIOR,
                    AlertTypeEnum.FIRE_DETECTED
            };
            boolean detected = Math.random() > 0.7;
            result.put("detected", detected);
            if (detected) {
                AlertTypeEnum behavior = behaviorTypes[(int) (Math.random() * behaviorTypes.length)];
                result.put("behaviorType", behavior.getCode());
                result.put("behaviorName", behavior.getName());
                result.put("confidence", 0.7 + Math.random() * 0.3);
                log.info("行为分析检测到异常：{}", result);
            } else {
                result.put("behaviorType", 0);
                result.put("behaviorName", "正常");
                result.put("confidence", 0.9);
                log.debug("行为分析未检测到异常");
            }
            return result;
        } catch (Exception e) {
            log.error("行为分析失败：", e);
            Map<String, Object> result = new HashMap<>();
            result.put("detected", false);
            result.put("behaviorType", 0);
            result.put("behaviorName", "分析失败");
            result.put("confidence", 0);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void sendAlert(AlertMessageDTO alert) {
        try {
            CameraDevice camera = cameraDeviceMapper.selectByDeviceId(alert.getCameraId());
            if (camera != null) {
                if (alert.getLongitude() == null) {
                    alert.setLongitude(camera.getLongitude());
                }
                if (alert.getLatitude() == null) {
                    alert.setLatitude(camera.getLatitude());
                }
                if (alert.getCameraName() == null || alert.getCameraName().isEmpty()) {
                    alert.setCameraName(camera.getDeviceName());
                }
            }
            if (alert.getAlertId() == null || alert.getAlertId().isEmpty()) {
                alert.setAlertId("A" + SnowflakeIdUtil.nextId());
            }
            if (alert.getAlertTime() == null) {
                alert.setAlertTime(LocalDateTime.now());
            }
            AlertRecord alertRecord = new AlertRecord();
            alertRecord.setId(SnowflakeIdUtil.nextId());
            alertRecord.setAlertId(alert.getAlertId());
            alertRecord.setAlertType(alert.getAlertType());
            alertRecord.setAlertLevel(alert.getAlertLevel());
            alertRecord.setCameraId(alert.getCameraId());
            alertRecord.setDescription(alert.getDescription());
            alertRecord.setSnapshotUrl(alert.getSnapshotUrl());
            alertRecord.setVideoClipUrl(alert.getVideoClipUrl());
            alertRecord.setLongitude(alert.getLongitude());
            alertRecord.setLatitude(alert.getLatitude());
            alertRecord.setDetectTime(alert.getAlertTime());
            alertRecord.setProcessed(0);
            alertRecordMapper.insert(alertRecord);
            mqUtil.sendVideoAlert(alert);
            if (alert.getExtraData() != null) {
                mqUtil.sendBehaviorAnalysis(alert);
            }
            log.info("行为分析告警已发送：alertId={}, alertType={}", alert.getAlertId(), alert.getAlertType());
        } catch (Exception e) {
            log.error("发送行为分析告警失败：", e);
            throw new RuntimeException("发送告警失败", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processBehaviorAlert(String cameraId, Integer behaviorType,
                                     BigDecimal longitude, BigDecimal latitude,
                                     String snapshotUrl, String videoClipUrl) {
        AlertTypeEnum alertType = AlertTypeEnum.getByCode(behaviorType);
        CameraDevice camera = cameraDeviceMapper.selectByDeviceId(cameraId);
        AlertMessageDTO alert = new AlertMessageDTO();
        alert.setAlertId("A" + SnowflakeIdUtil.nextId());
        alert.setAlertType(alertType.getCode());
        alert.setAlertName(alertType.getName());
        alert.setAlertLevel(alertType.getLevel());
        alert.setCameraId(cameraId);
        alert.setCameraName(camera != null ? camera.getDeviceName() : "");
        alert.setLongitude(longitude != null ? longitude : (camera != null ? camera.getLongitude() : null));
        alert.setLatitude(latitude != null ? latitude : (camera != null ? camera.getLatitude() : null));
        alert.setDescription(alertType.getName() + "检测告警");
        alert.setSnapshotUrl(snapshotUrl);
        alert.setVideoClipUrl(videoClipUrl);
        alert.setAlertTime(LocalDateTime.now());
        Map<String, Object> extra = new HashMap<>();
        extra.put("behaviorType", behaviorType);
        extra.put("analysisSource", "video_stream");
        alert.setExtraData(extra);
        sendAlert(alert);
    }
}
