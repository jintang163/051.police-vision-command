package com.police.vision.video.service;

import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.enums.AlertTypeEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.entity.PlateRecord;
import com.police.vision.video.entity.TargetVehicle;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlateRecognitionService {

    private final PlateRecordMapper plateRecordMapper;
    private final TargetVehicleMapper targetVehicleMapper;
    private final CameraDeviceMapper cameraDeviceMapper;
    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;

    private static final Pattern PLATE_PATTERN = Pattern.compile(
            "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领]" +
                    "[A-Z][A-HJ-NP-Z0-9]{4,5}[A-HJ-NP-Z0-9挂学警港澳]$");

    public Map<String, Object> recognizePlate(byte[] image) {
        try {
            log.info("车牌识别，图片大小：{} bytes", image.length);
            Map<String, Object> result = new HashMap<>();
            String plateNo = generateRandomPlate();
            result.put("plateNo", plateNo);
            result.put("vehicleColor", getRandomColor());
            result.put("vehicleType", getRandomVehicleType());
            result.put("confidence", 0.85 + Math.random() * 0.15);
            result.put("valid", PLATE_PATTERN.matcher(plateNo).matches());
            log.info("车牌识别结果：{}", result);
            return result;
        } catch (Exception e) {
            log.error("车牌识别失败：", e);
            throw new RuntimeException("车牌识别失败", e);
        }
    }

    private String generateRandomPlate() {
        String[] provinces = {"京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘",
                "皖", "鲁", "新", "苏", "浙", "赣", "鄂", "桂", "甘", "晋",
                "蒙", "陕", "吉", "闽", "贵", "粤", "青", "藏", "川", "宁", "琼"};
        String province = provinces[(int) (Math.random() * provinces.length)];
        String letter = String.valueOf((char) ('A' + (int) (Math.random() * 26)));
        StringBuilder numbers = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (Math.random() > 0.7) {
                numbers.append((char) ('A' + (int) (Math.random() * 26)));
            } else {
                numbers.append((int) (Math.random() * 10));
            }
        }
        return province + letter + numbers;
    }

    private String getRandomColor() {
        String[] colors = {"白色", "黑色", "灰色", "银色", "红色", "蓝色", "绿色", "黄色", "棕色", "金色"};
        return colors[(int) (Math.random() * colors.length)];
    }

    private String getRandomVehicleType() {
        String[] types = {"轿车", "SUV", "面包车", "货车", "客车", "摩托车", "电动车"};
        return types[(int) (Math.random() * types.length)];
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
            Map<String, Object> extra = new HashMap<>();
            extra.put("vehicleType", record.getVehicleType());
            extra.put("vehicleColor", record.getVehicleColor());
            extra.put("vehicleId", target.getVehicleId());
            alert.setExtraData(extra);
            mqUtil.sendVideoAlert(alert);
            log.info("车牌匹配告警已发送：plateNo={}, vehicleId={}", record.getPlateNo(), target.getVehicleId());
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
        if (plateNo == null || plateNo.isEmpty()) {
            return false;
        }
        Matcher matcher = PLATE_PATTERN.matcher(plateNo.toUpperCase());
        return matcher.matches();
    }
}
