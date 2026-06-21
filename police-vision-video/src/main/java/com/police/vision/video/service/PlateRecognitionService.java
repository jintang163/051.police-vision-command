package com.police.vision.video.service;

import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.enums.AlertTypeEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.video.client.Yolov8Client;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.entity.PlateRecord;
import com.police.vision.video.entity.TargetVehicle;
import com.police.vision.video.entity.VideoStorage;
import com.police.vision.video.mapper.CameraDeviceMapper;
import com.police.vision.video.mapper.PlateRecordMapper;
import com.police.vision.video.mapper.TargetVehicleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlateRecognitionService {

    private final PlateRecordMapper plateRecordMapper;
    private final TargetVehicleMapper targetVehicleMapper;
    private final CameraDeviceMapper cameraDeviceMapper;
    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;
    private final Yolov8Client yolov8Client;
    private final VideoClipService videoClipService;

    public Map<String, Object> recognizePlate(byte[] image) {
        try {
            log.info("调用YOLOv8车牌识别，图片大小：{} bytes", image.length);
            Map<String, Object> result = yolov8Client.detectPlate(image);
            log.info("车牌识别结果：plateNo={}, confidence={}",
                    result.get("plateNo"), result.get("confidence"));
            return result;
        } catch (Exception e) {
            log.error("车牌识别失败：", e);
            throw new RuntimeException("车牌识别失败", e);
        }
    }

    public Map<String, Object> detectAndRecognizePlate(byte[] image) {
        try {
            log.info("车牌检测与识别，图片大小：{} bytes", image.length);
            Map<String, Object> result = recognizePlate(image);

            String plateNo = (String) result.get("plateNo");
            if (plateNo != null && Boolean.TRUE.equals(result.get("valid"))) {
                TargetVehicle target = getTargetVehicleByPlateNo(plateNo);
                if (target != null && target.getStatus() == 1) {
                    result.put("isTarget", true);
                    result.put("targetInfo", target);
                    result.put("controlLevel", target.getControlLevel());
                } else {
                    result.put("isTarget", false);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("车牌检测失败：", e);
            Map<String, Object> result = new HashMap<>();
            result.put("valid", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addVehicleToTarget(TargetVehicle vehicle) {
        TargetVehicle exist = targetVehicleMapper.selectByPlateNo(vehicle.getPlateNo());
        if (exist != null) {
            vehicle.setId(exist.getId());
            targetVehicleMapper.updateById(vehicle);
        } else {
            vehicle.setId(SnowflakeIdUtil.nextId());
            vehicle.setVehicleId("V" + SnowflakeIdUtil.nextId());
            targetVehicleMapper.insert(vehicle);
        }
        redisUtil.delete(RedisConstant.TARGET_VEHICLE_KEY + "*");
        log.info("添加重点车辆布控成功：vehicleId={}, plateNo={}", vehicle.getVehicleId(), vehicle.getPlateNo());
    }

    public TargetVehicle getTargetVehicle(String vehicleId) {
        return targetVehicleMapper.selectByVehicleId(vehicleId);
    }

    public TargetVehicle getTargetVehicleByPlateNo(String plateNo) {
        String key = RedisConstant.TARGET_VEHICLE_KEY + "plate:" + plateNo;
        TargetVehicle cache = redisUtil.getObject(key, TargetVehicle.class);
        if (cache != null) {
            return cache;
        }
        TargetVehicle vehicle = targetVehicleMapper.selectByPlateNo(plateNo);
        if (vehicle != null) {
            redisUtil.setObject(key, vehicle, 1, TimeUnit.HOURS);
        }
        return vehicle;
    }

    public List<TargetVehicle> getTargetVehicleByStatus(Integer status) {
        String key = RedisConstant.TARGET_VEHICLE_KEY + "status:" + status;
        List<TargetVehicle> cache = redisUtil.getObject(key, List.class);
        if (cache != null) {
            return cache;
        }
        List<TargetVehicle> list = targetVehicleMapper.selectByStatus(status);
        redisUtil.setObject(key, list, 1, TimeUnit.HOURS);
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handlePlateMatch(PlateRecord record) {
        record.setId(SnowflakeIdUtil.nextId());
        record.setRecordId("PR" + SnowflakeIdUtil.nextId());
        if (record.getDetectTime() == null) {
            record.setDetectTime(LocalDateTime.now());
        }
        plateRecordMapper.insert(record);
        TargetVehicle target = targetVehicleMapper.selectByPlateNo(record.getPlateNo());
        if (target != null && target.getStatus() == 1) {
            CameraDevice camera = cameraDeviceMapper.selectByDeviceId(record.getCameraId());

            VideoStorage videoClip = null;
            try {
                videoClip = videoClipService.captureAlertVideo(
                        record.getCameraId(),
                        record.getDetectTime()
                );
                log.info("车牌匹配告警视频截取完成：storageId={}", videoClip.getStorageId());
            } catch (Exception e) {
                log.error("截取车牌匹配告警视频失败：{}", e.getMessage());
            }

            AlertMessageDTO alert = new AlertMessageDTO();
            alert.setAlertId("A" + SnowflakeIdUtil.nextId());
            alert.setAlertType(AlertTypeEnum.PLATE_MATCH.getCode());
            alert.setAlertName(AlertTypeEnum.PLATE_MATCH.getName());
            alert.setAlertLevel(target.getControlLevel());
            alert.setCameraId(record.getCameraId());
            alert.setCameraName(camera != null ? camera.getDeviceName() : "");
            alert.setLongitude(record.getLongitude());
            alert.setLatitude(record.getLatitude());
            alert.setDescription("重点车辆车牌识别告警：" + record.getPlateNo() +
                    "，车辆类型：" + record.getVehicleType() + "，颜色：" + record.getVehicleColor());
            alert.setSnapshotUrl(record.getSnapshotUrl());
            alert.setAlertTime(record.getDetectTime());
            alert.setTargetPlateNo(record.getPlateNo());
            if (videoClip != null) {
                try {
                    alert.setVideoClipUrl(videoClipService.getVideoUrl(videoClip.getFilePath()));
                } catch (Exception e) {
                    log.warn("获取告警视频URL失败：{}", e.getMessage());
                }
            }
            Map<String, Object> extra = new HashMap<>();
            extra.put("vehicleType", record.getVehicleType());
            extra.put("vehicleColor", record.getVehicleColor());
            extra.put("vehicleId", target.getVehicleId());
            extra.put("videoStorageId", videoClip != null ? videoClip.getStorageId() : null);
            alert.setExtraData(extra);
            mqUtil.sendVideoAlert(alert);
            log.info("车牌匹配告警已发送：plateNo={}, vehicleId={}, videoStorageId={}",
                    record.getPlateNo(), target.getVehicleId(),
                    videoClip != null ? videoClip.getStorageId() : null);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void savePlateRecord(PlateRecord record) {
        record.setId(SnowflakeIdUtil.nextId());
        record.setRecordId("PR" + SnowflakeIdUtil.nextId());
        if (record.getDetectTime() == null) {
            record.setDetectTime(LocalDateTime.now());
        }
        plateRecordMapper.insert(record);
    }

    public boolean validatePlate(String plateNo) {
        return yolov8Client.validatePlate(plateNo);
    }
}
