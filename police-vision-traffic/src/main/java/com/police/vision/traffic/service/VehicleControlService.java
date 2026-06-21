package com.police.vision.traffic.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.entity.*;
import com.police.vision.common.enums.AlertTypeEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.traffic.config.SentinelConfig;
import com.police.vision.traffic.mapper.VehicleControlAlertMapper;
import com.police.vision.traffic.mapper.VehicleControlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleControlService {

    private final VehicleControlMapper vehicleControlMapper;
    private final VehicleControlAlertMapper vehicleControlAlertMapper;
    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;

    @Transactional(rollbackFor = Exception.class)
    public void addVehicleControl(VehicleControl control) {
        control.setId(SnowflakeIdUtil.nextId());
        control.setControlNo("VC" + SnowflakeIdUtil.nextId());
        control.setStatus(1);
        control.setWarningCount(0);
        vehicleControlMapper.insert(control);
        refreshControlCache();
        log.info("添加车辆布控成功：controlNo={}, plateNo={}", control.getControlNo(), control.getPlateNo());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateVehicleControl(VehicleControl control) {
        vehicleControlMapper.updateById(control);
        refreshControlCache();
        log.info("更新车辆布控成功：controlNo={}", control.getControlNo());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteVehicleControl(Long id) {
        VehicleControl control = vehicleControlMapper.selectById(id);
        if (control != null) {
            control.setStatus(0);
            vehicleControlMapper.updateById(control);
            refreshControlCache();
            log.info("撤控成功：controlNo={}", control.getControlNo());
        }
    }

    public VehicleControl getVehicleControlById(Long id) {
        return vehicleControlMapper.selectById(id);
    }

    public IPage<VehicleControl> getVehicleControlList(int page, int size, String plateNo,
                                                       Integer status, Integer controlType) {
        Page<VehicleControl> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VehicleControl> wrapper = new LambdaQueryWrapper<>();
        if (plateNo != null && !plateNo.isEmpty()) {
            wrapper.like(VehicleControl::getPlateNo, plateNo);
        }
        if (status != null) {
            wrapper.eq(VehicleControl::getStatus, status);
        }
        if (controlType != null) {
            wrapper.eq(VehicleControl::getControlType, controlType);
        }
        wrapper.orderByDesc(VehicleControl::getCreateTime);
        return vehicleControlMapper.selectPage(pageParam, wrapper);
    }

    public List<VehicleControl> getActiveControls() {
        String cacheKey = RedisConstant.TARGET_VEHICLE_KEY + "active_controls";
        List<VehicleControl> cache = redisUtil.getObject(cacheKey, List.class);
        if (cache != null && !cache.isEmpty()) {
            return cache;
        }
        List<VehicleControl> controls = vehicleControlMapper.selectActiveControls();
        if (controls != null && !controls.isEmpty()) {
            redisUtil.setObject(cacheKey, controls, 30, TimeUnit.MINUTES);
        }
        return controls != null ? controls : Collections.emptyList();
    }

    private void refreshControlCache() {
        String cacheKey = RedisConstant.TARGET_VEHICLE_KEY + "active_controls";
        redisUtil.delete(cacheKey);
    }

    @SentinelResource(value = "vehicle_control_handle",
            blockHandler = "handleControlBlock")
    @Transactional(rollbackFor = Exception.class)
    public List<VehicleControlAlert> checkAndHandleControl(TrafficCaptureData captureData) {
        List<VehicleControlAlert> alerts = new ArrayList<>();
        if (captureData == null || captureData.getPlateNo() == null) {
            return alerts;
        }

        List<VehicleControl> activeControls = getActiveControls();
        if (activeControls.isEmpty()) {
            return alerts;
        }

        for (VehicleControl control : activeControls) {
            if (matchControl(control, captureData)) {
                VehicleControlAlert alert = createAlert(control, captureData);
                alerts.add(alert);
                saveAlert(alert);
                sendAlertNotification(alert);
                updateControlWarning(control);
                log.warn("车辆布控告警触发：plateNo={}, controlNo={}, crossing={}",
                        captureData.getPlateNo(), control.getControlNo(), captureData.getCrossingName());
            }
        }

        return alerts;
    }

    public List<VehicleControlAlert> handleControlBlock(TrafficCaptureData captureData, BlockException ex) {
        log.warn("车辆布控处理触发Sentinel限流：plateNo={}, rule={}",
                captureData.getPlateNo(), ex.getRule().getResource());
        return Collections.emptyList();
    }

    private boolean matchControl(VehicleControl control, TrafficCaptureData data) {
        if (control.getStatus() != 1) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (control.getStartTime() != null && now.isBefore(control.getStartTime())) {
            return false;
        }
        if (control.getEndTime() != null && now.isAfter(control.getEndTime())) {
            return false;
        }

        if (control.getPlateNo() != null && !control.getPlateNo().isEmpty()) {
            if (!control.getPlateNo().equals(data.getPlateNo())) {
                if (!control.getPlateNo().contains("*") && !control.getPlateNo().contains("?")) {
                    return false;
                }
                if (!matchPlatePattern(control.getPlateNo(), data.getPlateNo())) {
                    return false;
                }
            }
        }

        if (control.getVehicleType() != null && !control.getVehicleType().isEmpty()) {
            if (!control.getVehicleType().equals(data.getVehicleType())) {
                return false;
            }
        }

        if (control.getVehicleColor() != null && !control.getVehicleColor().isEmpty()) {
            if (!control.getVehicleColor().equals(data.getVehicleColor())) {
                return false;
            }
        }

        boolean hasAreaConfig = (control.getAreaCode() != null && !control.getAreaCode().isEmpty())
                || (control.getCrossingIds() != null && !control.getCrossingIds().isEmpty());
        if (hasAreaConfig) {
            if (data.getCrossingId() == null || !isCrossingMatch(data.getCrossingId(), control.getAreaCode(), control.getCrossingIds())) {
                return false;
            }
        }

        if (control.getTimeRules() != null && !control.getTimeRules().isEmpty()) {
            if (!matchTimeRule(control.getTimeRules(), data.getCaptureTime())) {
                return false;
            }
        }

        return true;
    }

    private boolean matchPlatePattern(String pattern, String plateNo) {
        String regex = pattern.replace("*", ".*").replace("?", ".");
        return plateNo.matches(regex);
    }

    private boolean isCrossingMatch(String crossingId, String areaCode, String crossingIds) {
        if ((areaCode == null || areaCode.isEmpty()) && (crossingIds == null || crossingIds.isEmpty())) {
            return true;
        }

        if (crossingIds != null && !crossingIds.isEmpty()) {
            Set<String> crossingSet = new HashSet<>(Arrays.asList(crossingIds.split(",")));
            if (crossingSet.contains(crossingId)) {
                return true;
            }
        }

        if (areaCode != null && !areaCode.isEmpty()) {
            String areaPrefix = areaCode.length() > 6 ? areaCode.substring(0, 6) : areaCode;
            if (crossingId != null && crossingId.startsWith(areaPrefix)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchTimeRule(String timeRules, LocalDateTime captureTime) {
        try {
            LocalTime captureLocalTime = captureTime.toLocalTime();
            String[] rules = timeRules.split(";");
            for (String rule : rules) {
                String[] times = rule.split("-");
                if (times.length == 2) {
                    LocalTime start = LocalTime.parse(times[0].trim());
                    LocalTime end = LocalTime.parse(times[1].trim());
                    if (end.isBefore(start)) {
                        if (captureLocalTime.isAfter(start) || captureLocalTime.isBefore(end)) {
                            return true;
                        }
                    } else {
                        if (captureLocalTime.isAfter(start) && captureLocalTime.isBefore(end)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.error("时间规则匹配失败：rule={}, error={}", timeRules, e.getMessage());
            return true;
        }
    }

    private VehicleControlAlert createAlert(VehicleControl control, TrafficCaptureData data) {
        VehicleControlAlert alert = new VehicleControlAlert();
        alert.setId(SnowflakeIdUtil.nextId());
        alert.setAlertNo("VCA" + SnowflakeIdUtil.nextId());
        alert.setControlId(control.getId().toString());
        alert.setControlNo(control.getControlNo());
        alert.setControlName(control.getControlName());
        alert.setControlLevel(control.getControlLevel());
        alert.setAlertType(AlertTypeEnum.VEHICLE_CONTROL.getCode());
        alert.setAlertName(AlertTypeEnum.VEHICLE_CONTROL.getName());
        alert.setAlertLevel(control.getControlLevel());
        alert.setPlateNo(data.getPlateNo());
        alert.setPlateColor(data.getPlateColor());
        alert.setVehicleType(data.getVehicleType());
        alert.setVehicleColor(data.getVehicleColor());
        alert.setVehicleBrand(data.getVehicleBrand());
        alert.setCrossingId(data.getCrossingId());
        alert.setCrossingName(data.getCrossingName());
        alert.setCameraId(data.getCameraId());
        alert.setCameraName(data.getCameraName());
        alert.setLaneNo(data.getLaneNo());
        alert.setSpeed(data.getSpeed());
        alert.setDirection(data.getDirection());
        alert.setLongitude(data.getLongitude());
        alert.setLatitude(data.getLatitude());
        alert.setImageUrl(data.getImageUrl());
        alert.setPlateImageUrl(data.getPlateImageUrl());
        alert.setCaptureTime(data.getCaptureTime());
        alert.setAlertTime(LocalDateTime.now());
        alert.setDescription("车辆布控告警：" + data.getPlateNo() + " 经过 " + data.getCrossingName());
        alert.setStatus(1);
        alert.setPushStatus(0);
        return alert;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveAlert(VehicleControlAlert alert) {
        vehicleControlAlertMapper.insert(alert);
    }

    private void sendAlertNotification(VehicleControlAlert alert) {
        try {
            AlertMessageDTO alertMessage = new AlertMessageDTO();
            alertMessage.setAlertId(alert.getAlertNo());
            alertMessage.setAlertType(alert.getAlertType());
            alertMessage.setAlertName(alert.getAlertName());
            alertMessage.setAlertLevel(alert.getAlertLevel());
            alertMessage.setCameraId(alert.getCameraId());
            alertMessage.setCameraName(alert.getCameraName());
            alertMessage.setLongitude(alert.getLongitude());
            alertMessage.setLatitude(alert.getLatitude());
            alertMessage.setDescription(alert.getDescription());
            alertMessage.setSnapshotUrl(alert.getImageUrl());
            alertMessage.setAlertTime(alert.getAlertTime());
            alertMessage.setTargetPlateNo(alert.getPlateNo());

            Map<String, Object> extra = new HashMap<>();
            extra.put("vehicleType", alert.getVehicleType());
            extra.put("vehicleColor", alert.getVehicleColor());
            extra.put("controlNo", alert.getControlNo());
            extra.put("controlName", alert.getControlName());
            extra.put("crossingName", alert.getCrossingName());
            alertMessage.setExtraData(extra);

            mqUtil.sendVideoAlert(alertMessage);
            log.info("车辆布控告警已发送MQ：alertNo={}", alert.getAlertNo());
        } catch (Exception e) {
            log.error("车辆布控告警发送失败：", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateControlWarning(VehicleControl control) {
        control.setWarningCount(control.getWarningCount() == null ? 1 : control.getWarningCount() + 1);
        control.setLastWarningTime(LocalDateTime.now());
        vehicleControlMapper.updateById(control);
    }

    public IPage<VehicleControlAlert> getAlertList(int page, int size, String plateNo,
                                                    Integer alertType, Integer status) {
        Page<VehicleControlAlert> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VehicleControlAlert> wrapper = new LambdaQueryWrapper<>();
        if (plateNo != null && !plateNo.isEmpty()) {
            wrapper.like(VehicleControlAlert::getPlateNo, plateNo);
        }
        if (alertType != null) {
            wrapper.eq(VehicleControlAlert::getAlertType, alertType);
        }
        if (status != null) {
            wrapper.eq(VehicleControlAlert::getStatus, status);
        }
        wrapper.orderByDesc(VehicleControlAlert::getAlertTime);
        return vehicleControlAlertMapper.selectPage(pageParam, wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(Long alertId, String handleResult, String handleRemark, Long handlerId, String handlerName) {
        VehicleControlAlert alert = vehicleControlAlertMapper.selectById(alertId);
        if (alert != null) {
            alert.setStatus(3);
            alert.setHandlerId(handlerId);
            alert.setHandlerName(handlerName);
            alert.setHandleTime(LocalDateTime.now());
            alert.setHandleResult(handleResult);
            alert.setHandleRemark(handleRemark);
            vehicleControlAlertMapper.updateById(alert);
            log.info("告警处理完成：alertId={}, result={}", alertId, handleResult);
        }
    }
}
