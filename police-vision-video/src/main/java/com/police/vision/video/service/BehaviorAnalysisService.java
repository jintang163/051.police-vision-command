package com.police.vision.video.service;

import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.enums.AlertTypeEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.video.client.Yolov8Client;
import com.police.vision.video.entity.AlertRecord;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.entity.VideoStorage;
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
    private final Yolov8Client yolov8Client;
    private final VideoClipService videoClipService;

    public Map<String, Object> analyzeBehavior(byte[] frame, String cameraId) {
        try {
            log.info("调用YOLOv8行为分析：cameraId={}, frameSize={} bytes", cameraId, frame != null ? frame.length : 0);
            Map<String, Object> result = yolov8Client.analyzeBehavior(frame);
            if (Boolean.TRUE.equals(result.get("detected"))) {
                log.info("行为分析检测到异常：behaviorType={}, behaviorName={}, confidence={}",
                        result.get("behaviorType"), result.get("behaviorName"), result.get("confidence"));
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
    public Map<String, Object> processBehaviorFrame(byte[] frame, String cameraId) {
        Map<String, Object> result = analyzeBehavior(frame, cameraId);

        if (Boolean.TRUE.equals(result.get("detected"))) {
            Integer behaviorType = (Integer) result.get("behaviorType");
            CameraDevice camera = cameraDeviceMapper.selectByDeviceId(cameraId);

            VideoStorage videoClip = null;
            try {
                videoClip = videoClipService.captureAlertVideo(cameraId, LocalDateTime.now());
                log.info("行为分析告警视频截取完成：storageId={}", videoClip.getStorageId());
            } catch (Exception e) {
                log.error("截取行为分析告警视频失败：{}", e.getMessage());
            }

            AlertTypeEnum alertType = AlertTypeEnum.getByCode(behaviorType);
            AlertMessageDTO alert = new AlertMessageDTO();
            alert.setAlertId("A" + SnowflakeIdUtil.nextId());
            alert.setAlertType(alertType != null ? alertType.getCode() : AlertTypeEnum.ABNORMAL_BEHAVIOR.getCode());
            alert.setAlertName(alertType != null ? alertType.getName() : "异常行为");
            alert.setAlertLevel(alertType != null ? alertType.getLevel() : 2);
            alert.setCameraId(cameraId);
            alert.setCameraName(camera != null ? camera.getDeviceName() : "");
            alert.setLongitude(camera != null ? camera.getLongitude() : null);
            alert.setLatitude(camera != null ? camera.getLatitude() : null);
            alert.setDescription((String) result.get("behaviorName") + "检测告警");
            alert.setAlertTime(LocalDateTime.now());
            if (videoClip != null) {
                try {
                    alert.setVideoClipUrl(videoClipService.getVideoUrl(videoClip.getFilePath()));
                } catch (Exception e) {
                    log.warn("获取告警视频URL失败：{}", e.getMessage());
                }
            }

            Map<String, Object> extra = new HashMap<>();
            extra.put("confidence", result.get("confidence"));
            extra.put("peopleCount", result.get("peopleCount"));
            extra.put("bbox", result.get("bbox"));
            extra.put("trackId", result.get("trackId"));
            extra.put("videoStorageId", videoClip != null ? videoClip.getStorageId() : null);
            alert.setExtraData(extra);

            sendAlert(alert);
            result.put("alertSent", true);
            result.put("alertId", alert.getAlertId());
        } else {
            result.put("alertSent", false);
        }

        return result;
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
