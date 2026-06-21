package com.police.vision.control.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.enums.AlertTypeEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.entity.*;
import com.police.vision.control.mapper.*;
import com.police.vision.mobile.service.DispatchMobileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoFenceService {

    private final GeoFenceMapper geoFenceMapper;
    private final FenceAlertMapper fenceAlertMapper;
    private final TargetPersonMapper targetPersonMapper;
    private final MqUtil mqUtil;
    private final DispatchMobileService dispatchMobileService;

    private static final Map<String, Set<String>> PERSON_FENCE_CACHE = new ConcurrentHashMap<>();

    public List<GeoFence> listFences(String fenceType, String stationCode, Boolean enabled) {
        LambdaQueryWrapper<GeoFence> wrapper = new LambdaQueryWrapper<>();
        if (fenceType != null && !fenceType.isEmpty()) {
            wrapper.eq(GeoFence::getFenceType, fenceType);
        }
        if (stationCode != null && !stationCode.isEmpty()) {
            wrapper.eq(GeoFence::getPoliceStationCode, stationCode);
        }
        if (enabled != null) {
            wrapper.eq(GeoFence::getEnabled, enabled);
        }
        wrapper.orderByDesc(GeoFence::getFenceLevel);
        return geoFenceMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public GeoFence createFence(GeoFence fence) {
        if (fence.getFenceId() == null) {
            fence.setFenceId("GF" + SnowflakeIdUtil.nextId());
        }
        if (fence.getId() == null) {
            fence.setId(SnowflakeIdUtil.nextId());
        }
        if (fence.getEnabled() == null) {
            fence.setEnabled(true);
        }
        fillFenceTypeName(fence);
        geoFenceMapper.insert(fence);
        log.info("创建电子围栏：fenceId={}, fenceName={}, type={}",
                fence.getFenceId(), fence.getFenceName(), fence.getFenceType());
        return fence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateFence(GeoFence fence) {
        fillFenceTypeName(fence);
        geoFenceMapper.updateById(fence);
        log.info("更新电子围栏：fenceId={}", fence.getFenceId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFence(String fenceId) {
        LambdaQueryWrapper<GeoFence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GeoFence::getFenceId, fenceId);
        geoFenceMapper.delete(wrapper);
        log.info("删除电子围栏：fenceId={}", fenceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void checkAndTriggerFenceAlert(String personId, String personName, BigDecimal longitude,
                                           BigDecimal latitude, String cameraId, String cameraName,
                                           String snapshotUrl, String videoClipUrl) {
        TargetPerson person = targetPersonMapper.selectByPersonId(personId);
        if (person == null || person.getStatus() != 1) {
            return;
        }

        List<GeoFence> fences = geoFenceMapper.selectByEnabled(true);
        for (GeoFence fence : fences) {
            boolean inside = isPointInsideFence(fence, longitude, latitude);
            if (!inside) continue;

            int alertLevel = calculateAlertLevel(person, fence);

            FenceAlert active = fenceAlertMapper.selectActiveAlert(personId, fence.getFenceId());
            if (active != null) {
                log.debug("人员已在围栏内，不重复告警：personId={}, fenceId={}", personId, fence.getFenceId());
                continue;
            }

            FenceAlert alert = createFenceAlert(person, fence, longitude, latitude,
                    cameraId, cameraName, snapshotUrl, videoClipUrl, alertLevel);

            PERSON_FENCE_CACHE.computeIfAbsent(personId, k -> ConcurrentHashMap.newKeySet())
                    .add(fence.getFenceId());

            sendFenceAlertMq(alert, person);

            if (dispatchMobileService != null) {
                pushVisitorToPolice(person, fence, alert);
            }

            log.warn("电子围栏告警：person={}[{}] 进入围栏={}[{}]，级别={}",
                    personName, personId, fence.getFenceName(), fence.getFenceId(), alertLevel);
        }
    }

    public void markPersonLeaveFence(String personId, String fenceId) {
        LambdaQueryWrapper<FenceAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FenceAlert::getPersonId, personId)
                .eq(FenceAlert::getFenceId, fenceId)
                .eq(FenceAlert::getStatus, 0);
        List<FenceAlert> actives = fenceAlertMapper.selectList(wrapper);
        for (FenceAlert alert : actives) {
            alert.setStatus(1);
            alert.setStatusName("已离开");
            alert.setLeaveTime(LocalDateTime.now());
            fenceAlertMapper.updateById(alert);
            log.info("人员离开电子围栏：personId={}, fenceId={}, 停留秒数={}",
                    personId, fenceId,
                    java.time.Duration.between(alert.getAlertTime(), alert.getLeaveTime()).getSeconds());
        }
        Set<String> set = PERSON_FENCE_CACHE.get(personId);
        if (set != null) set.remove(fenceId);
    }

    private void fillFenceTypeName(GeoFence fence) {
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("sensitive", "敏感区域");
        typeMap.put("goverment", "党政机关");
        typeMap.put("school", "学校");
        typeMap.put("hospital", "医院");
        typeMap.put("station", "交通枢纽");
        typeMap.put("market", "商业中心");
        typeMap.put("residence", "居民区");
        typeMap.put("border", "辖区边界");
        typeMap.put("forbidden", "禁入区域");
        if (fence.getFenceType() != null) {
            fence.setFenceTypeName(typeMap.getOrDefault(fence.getFenceType(), "其他区域"));
        }
    }

    private boolean isPointInsideFence(GeoFence fence, BigDecimal longitude, BigDecimal latitude) {
        if (fence.getRadius() != null && fence.getCenterLongitude() != null && fence.getCenterLatitude() != null) {
            double distance = calculateDistance(
                    latitude.doubleValue(), longitude.doubleValue(),
                    fence.getCenterLatitude().doubleValue(), fence.getCenterLongitude().doubleValue());
            return distance <= fence.getRadius().doubleValue() / 1000.0;
        }
        if (fence.getPolygonPoints() != null && !fence.getPolygonPoints().isEmpty()) {
            return isPointInPolygon(longitude.doubleValue(), latitude.doubleValue(), fence.getPolygonPoints());
        }
        return false;
    }

    private int calculateAlertLevel(TargetPerson person, GeoFence fence) {
        int level = 1;
        if (person.getControlLevel() != null) level += person.getControlLevel();
        if (fence.getFenceLevel() != null) level += fence.getFenceLevel();
        if ("forbidden".equals(fence.getFenceType())) level += 2;
        if ("sensitive".equals(fence.getFenceType())) level += 1;
        if ("前科人员".equals(person.getPersonTypeName()) || "CRIMINAL".equals(person.getPersonType())) level += 1;
        if ("上访人员".equals(person.getPersonTypeName()) && "goverment".equals(fence.getFenceType())) level += 2;
        if ("精神障碍".equals(person.getPersonTypeName()) && "school".equals(fence.getFenceType())) level += 2;
        return Math.min(level, 4);
    }

    private FenceAlert createFenceAlert(TargetPerson person, GeoFence fence, BigDecimal longitude,
                                         BigDecimal latitude, String cameraId, String cameraName,
                                         String snapshotUrl, String videoClipUrl, int alertLevel) {
        FenceAlert alert = new FenceAlert();
        alert.setId(SnowflakeIdUtil.nextId());
        alert.setAlertId("FA" + SnowflakeIdUtil.nextId());
        alert.setAlertNo("FA" + System.currentTimeMillis());
        alert.setPersonId(person.getPersonId());
        alert.setPersonName(person.getPersonName());
        alert.setPersonType(person.getPersonType());
        alert.setAlertLevel(alertLevel);
        alert.setFenceId(fence.getFenceId());
        alert.setFenceName(fence.getFenceName());
        alert.setFenceType(fence.getFenceType());
        alert.setFenceTypeName(fence.getFenceTypeName());
        alert.setAlertLongitude(longitude);
        alert.setAlertLatitude(latitude);
        alert.setCameraId(cameraId);
        alert.setCameraName(cameraName);
        alert.setAlertType(1);
        alert.setAlertTypeName("进入围栏");
        alert.setAlertTime(LocalDateTime.now());
        alert.setStatus(0);
        alert.setStatusName("在围栏内");
        alert.setSnapshotUrl(snapshotUrl);
        alert.setVideoClipUrl(videoClipUrl);
        alert.setDescription(String.format("%s人员[%s]进入%s[%s]",
                person.getPersonTypeName() != null ? person.getPersonTypeName() : "重点",
                person.getPersonName(),
                fence.getFenceTypeName(),
                fence.getFenceName()));
        alert.setPoliceStationCode(fence.getPoliceStationCode());
        alert.setPoliceStationName(fence.getPoliceStationName());
        alert.setVisitorPushed(false);
        fenceAlertMapper.insert(alert);
        return alert;
    }

    private void sendFenceAlertMq(FenceAlert alert, TargetPerson person) {
        try {
            AlertMessageDTO dto = new AlertMessageDTO();
            dto.setAlertId(alert.getAlertId());
            dto.setAlertType(AlertTypeEnum.FENCE_BREACH.getCode());
            dto.setAlertName(AlertTypeEnum.FENCE_BREACH.getName());
            dto.setAlertLevel(alert.getAlertLevel());
            dto.setCameraId(alert.getCameraId());
            dto.setCameraName(alert.getCameraName());
            dto.setLongitude(alert.getAlertLongitude());
            dto.setLatitude(alert.getAlertLatitude());
            dto.setDescription(alert.getDescription());
            dto.setSnapshotUrl(alert.getSnapshotUrl());
            dto.setAlertTime(alert.getAlertTime());
            dto.setTargetPersonId(person.getPersonId());
            dto.setTargetPersonName(person.getPersonName());
            dto.setVideoClipUrl(alert.getVideoClipUrl());
            Map<String, Object> extra = new HashMap<>();
            extra.put("fenceId", alert.getFenceId());
            extra.put("fenceName", alert.getFenceName());
            extra.put("fenceType", alert.getFenceType());
            extra.put("personType", person.getPersonType());
            extra.put("controlLevel", person.getControlLevel());
            dto.setExtraData(extra);

            mqUtil.send(MqConstant.VIDEO_ALERT_TOPIC + ":" + MqConstant.TAG_ALERT, dto);
            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("fence_alert", alert));
            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("alert", dto));
        } catch (Exception e) {
            log.error("发送围栏告警失败：alertId={}", alert.getAlertId(), e);
        }
    }

    private void pushVisitorToPolice(TargetPerson person, GeoFence fence, FenceAlert alert) {
        String stationCode = fence.getPoliceStationCode();
        if (stationCode == null || stationCode.isEmpty()) return;

        try {
            List<TargetPerson> officers = targetPersonMapper.selectByStationCode(stationCode);
            List<Long> officerIds = new ArrayList<>();
            for (TargetPerson officer : officers) {
                Long id = Long.parseLong(officer.getPersonId().replace("P", ""));
                officerIds.add(id);
            }

            if (!officerIds.isEmpty()) {
                Map<String, Object> visitorInfo = new HashMap<>();
                visitorInfo.put("type", "visitor_push");
                visitorInfo.put("visitorId", person.getPersonId());
                visitorInfo.put("visitorName", person.getPersonName());
                visitorInfo.put("personType", person.getPersonTypeName());
                visitorInfo.put("controlLevel", person.getControlLevel());
                visitorInfo.put("avatarUrl", person.getAvatarUrl());
                visitorInfo.put("fenceName", fence.getFenceName());
                visitorInfo.put("fenceTypeName", fence.getFenceTypeName());
                visitorInfo.put("address", person.getResidentAddress());
                visitorInfo.put("phone", person.getPhone());
                visitorInfo.put("alertTime", alert.getAlertTime());
                visitorInfo.put("alertLevel", alert.getAlertLevel());
                visitorInfo.put("snapshotUrl", alert.getSnapshotUrl());
                visitorInfo.put("longitude", alert.getAlertLongitude());
                visitorInfo.put("latitude", alert.getAlertLatitude());
                visitorInfo.put("description", alert.getDescription());

                for (Long officerId : officerIds) {
                    Map<String, Object> wsMsg = mqUtil.buildWebSocketMessage("visitor_push", visitorInfo);
                    wsMsg.put("policeId", officerId);
                    mqUtil.sendWebsocketScreenPush(wsMsg);
                }
                alert.setVisitorPushed(true);
                alert.setVisitorPushTime(LocalDateTime.now());
                fenceAlertMapper.updateById(alert);
                log.info("访客推送完成：visitor={}, station={}, officers={}",
                        person.getPersonId(), stationCode, officerIds.size());
            }
        } catch (Exception e) {
            log.error("访客推送失败：visitorId={}", person.getPersonId(), e);
        }
    }

    public double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        return s * 6378.137;
    }

    public boolean isPointInPolygon(double px, double py, String polygonStr) {
        try {
            List<double[]> points = new ArrayList<>();
            String[] parts = polygonStr.split(";");
            for (String part : parts) {
                String[] lnglat = part.split(",");
                if (lnglat.length == 2) {
                    points.add(new double[]{Double.parseDouble(lnglat[0]), Double.parseDouble(lnglat[1])});
                }
            }
            if (points.size() < 3) return false;
            int count = points.size();
            boolean inside = false;
            for (int i = 0, j = count - 1; i < count; j = i++) {
                double xi = points.get(i)[0], yi = points.get(i)[1];
                double xj = points.get(j)[0], yj = points.get(j)[1];
                if ((yi > py) != (yj > py) &&
                        (px < (xj - xi) * (py - yi) / (yj - yi + 1e-12) + xi)) {
                    inside = !inside;
                }
            }
            return inside;
        } catch (Exception e) {
            return false;
        }
    }

    public List<FenceAlert> listAlerts(Integer status, String personId, String fenceId,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<FenceAlert> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(FenceAlert::getStatus, status);
        if (personId != null) wrapper.eq(FenceAlert::getPersonId, personId);
        if (fenceId != null) wrapper.eq(FenceAlert::getFenceId, fenceId);
        if (startTime != null && endTime != null) {
            wrapper.between(FenceAlert::getAlertTime, startTime, endTime);
        }
        wrapper.orderByDesc(FenceAlert::getAlertTime);
        return fenceAlertMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(String alertId, String statusName, String remark, Long officerId) {
        LambdaQueryWrapper<FenceAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FenceAlert::getAlertId, alertId);
        FenceAlert alert = fenceAlertMapper.selectOne(wrapper);
        if (alert == null) {
            throw new RuntimeException("告警不存在");
        }
        alert.setStatus(2);
        alert.setStatusName(statusName != null ? statusName : "已处理");
        alert.setHandleRemark(remark);
        alert.setHandleOfficerId(officerId);
        alert.setHandleTime(LocalDateTime.now());
        fenceAlertMapper.updateById(alert);
        log.info("电子围栏告警已处理：alertId={}, officerId={}", alertId, officerId);
    }
}
