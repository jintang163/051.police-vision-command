package com.police.vision.alarm.service;

import com.alibaba.fastjson2.JSON;
import com.police.vision.alarm.entity.AlarmOrder;
import com.police.vision.alarm.entity.DispatchRecord;
import com.police.vision.alarm.entity.PoliceOfficer;
import com.police.vision.alarm.mapper.AlarmOrderMapper;
import com.police.vision.alarm.mapper.DispatchRecordMapper;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.OfficerEtaResultDTO;
import com.police.vision.common.dto.YawDetectionResultDTO;
import com.police.vision.common.entity.DispatchTrafficSnapshot;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class YawDetectionService {

    private final RedisUtil redisUtil;
    private final AmapRouteClientService amapRouteClientService;
    private final SmartDispatchService smartDispatchService;
    private final DispatchRecordMapper dispatchRecordMapper;
    private final AlarmOrderMapper alarmOrderMapper;
    private final MqUtil mqUtil;

    private static final double DEFAULT_YAW_THRESHOLD_METERS = 200.0;
    private static final int MAX_YAW_BEFORE_RECALC = 3;
    private static final long RECALC_COOLDOWN_SECONDS = 180;

    public YawDetectionResultDTO checkYaw(Long dispatchId, Long policeId,
                                   BigDecimal currentLon, BigDecimal currentLat,
                                   String routePolyline) {
        YawDetectionResultDTO result = YawDetectionResultDTO.builder()
                .dispatchId(dispatchId)
                .policeId(policeId)
                .currentLongitude(currentLon)
                .currentLatitude(currentLat)
                .detectTime(LocalDateTime.now())
                .deviationThreshold(BigDecimal.valueOf(DEFAULT_YAW_THRESHOLD_METERS))
                .yawCount(getYawCount(dispatchId, policeId))
                .build();

        DispatchRecord dispatch = dispatchRecordMapper.selectById(dispatchId);
        if (dispatch == null) {
            result.setYaw(false);
            result.setYawReason("派单记录不存在");
            return result;
        }
        result.setDispatchNo(dispatch.getDispatchNo());

        AlarmOrder alarm = alarmOrderMapper.selectById(dispatch.getAlarmId());
        if (alarm == null) {
            result.setYaw(false);
            result.setYawReason("警情信息不存在");
            return result;
        }

        BigDecimal alarmLon = alarm.getLongitude();
        BigDecimal alarmLat = alarm.getLatitude();

        double maxDeviation = 0;
        BigDecimal expectedLon = null;
        BigDecimal expectedLat = null;

        if (routePolyline != null && !routePolyline.isEmpty()) {
            List<double[]> routePoints = parsePolyline(routePolyline);
            if (routePoints.size() >= 2) {
                for (int i = 0; i < routePoints.size() - 1; i++) {
                    double[] p1 = routePoints.get(i);
                    double[] p2 = routePoints.get(i + 1);
                    BigDecimal deviation = AmapRouteClientService.calculatePointToLineDistanceMeters(
                            currentLon, currentLat,
                            BigDecimal.valueOf(p1[0]), BigDecimal.valueOf(p1[1]),
                            BigDecimal.valueOf(p2[0]), BigDecimal.valueOf(p2[1]));
                    if (deviation != null && deviation.doubleValue() > maxDeviation) {
                        maxDeviation = deviation.doubleValue();
                        double dx = p2[0] - p1[0];
                        double dy = p2[1] - p1[1];
                        double lenSq = dx * dx + dy * dy;
                        double t = lenSq > 0
                                ? Math.max(0, Math.min(1, ((currentLon.doubleValue() - p1[0]) * dx + (currentLat.doubleValue() - p1[1]) * dy) / lenSq))
                                : 0.5;
                        expectedLon = BigDecimal.valueOf(p1[0] + t * dx);
                        expectedLat = BigDecimal.valueOf(p1[1] + t * dy);
                    }
                }
            } else {
                maxDeviation = AmapRouteClientService.calculateStraightDistanceKm(
                        currentLon, currentLat, alarmLon, alarmLat) * 1000;
                expectedLon = alarmLon;
                expectedLat = alarmLat;
            }
        } else {
            maxDeviation = AmapRouteClientService.calculateStraightDistanceKm(
                    currentLon, currentLat, alarmLon, alarmLat) * 1000;
            expectedLon = alarmLon;
            expectedLat = alarmLat;
        }

        result.setDeviationMeters(BigDecimal.valueOf(maxDeviation).setScale(2, java.math.RoundingMode.HALF_UP));
        result.setExpectedLongitude(expectedLon);
        result.setExpectedLatitude(expectedLat);

        boolean isYaw = maxDeviation > DEFAULT_YAW_THRESHOLD_METERS;
        result.setYaw(isYaw);

        if (isYaw) {
            int newCount = incrementYawCount(dispatchId, policeId);
            result.setYawCount(newCount);
            result.setYawReason(String.format("偏离规划路线%.0f米（阈值%.0f米）",
                    maxDeviation, DEFAULT_YAW_THRESHOLD_METERS));

            boolean needRecalc = newCount >= MAX_YAW_BEFORE_RECALC;
            result.setNeedRecalc(needRecalc);

            if (needRecalc && canRecalcNow(dispatchId)) {
                result.setAutoRerouted(true);
                OfficerEtaResultDTO newEta = amapRouteClientService.calculateOfficerEta(
                        policeId, null,
                        currentLon, currentLat,
                        alarmLon, alarmLat);
                result.setNewEta(newEta);

                updateDispatchWithNewRoute(dispatchId, policeId, newEta);

                updateYawCheckRoute(dispatchId, policeId, newEta.getRoutePolyline());

                notifyReroute(dispatch, alarm, policeId, newEta);

                log.warn("【偏航重算】派单{}警员{}偏航{}次，已自动重算路线，新ETA: {}分钟",
                        dispatch.getDispatchNo(), policeId, newCount,
                        newEta.getEtaSeconds() != null ? newEta.getEtaSeconds() / 60 : null);
            }
        } else {
            result.setYawReason(String.format("偏离路线%.0f米，在正常范围内", maxDeviation));
        }

        saveDispatchTrack(dispatchId, policeId, currentLon, currentLat);

        return result;
    }

    @Scheduled(fixedDelay = 30_000)
    public void scheduledBatchYawCheck() {
        Set<String> activeKeys = redisUtil.keys(RedisConstant.DISPATCH_YAW_CHECK_PREFIX + "*");
        if (activeKeys == null || activeKeys.isEmpty()) return;

        int checked = 0;
        int yawDetected = 0;
        for (String key : activeKeys) {
            try {
                Map<String, Object> meta = redisUtil.getMap(key);
                if (meta == null) continue;

                Long dispatchId = toLong(meta.get("dispatchId"));
                Long policeId = toLong(meta.get("policeId"));
                String polyline = meta.get("polyline") != null ? meta.get("polyline").toString() : null;

                if (dispatchId == null || policeId == null) continue;

                BigDecimal[] realtimeLocation = readRealtimeLocation(policeId);
                if (realtimeLocation == null) continue;

                BigDecimal lon = realtimeLocation[0];
                BigDecimal lat = realtimeLocation[1];

                YawDetectionResultDTO yawResult = checkYaw(dispatchId, policeId, lon, lat, polyline);
                checked++;
                if (Boolean.TRUE.equals(yawResult.getYaw())) {
                    yawDetected++;
                }
            } catch (Exception e) {
                log.warn("批量偏航检查失败：{}", e.getMessage());
            }
        }
        if (checked > 0) {
            log.info("批量偏航检查完成：检查数={}, 偏航数={}", checked, yawDetected);
        }
    }

    private BigDecimal[] readRealtimeLocation(Long policeId) {
        String locationKey = RedisConstant.POLICE_LOCATION_PREFIX + policeId;
        String locationStr = redisUtil.get(locationKey);
        if (locationStr == null) return null;
        try {
            GpsLocation gps = JSON.parseObject(locationStr, GpsLocation.class);
            if (gps != null && gps.getLongitude() != null && gps.getLatitude() != null) {
                return new BigDecimal[]{gps.getLongitude(), gps.getLatitude()};
            }
        } catch (Exception e) {
            log.warn("解析警员{}实时定位失败：{}", policeId, e.getMessage());
        }
        return null;
    }

    public void registerDispatchYawCheck(Long dispatchId, Long policeId,
                               BigDecimal lon, BigDecimal lat,
                               String polyline) {
        String key = RedisConstant.DISPATCH_YAW_CHECK_PREFIX + dispatchId + "_" + policeId;
        Map<String, Object> meta = new HashMap<>();
        meta.put("dispatchId", dispatchId);
        meta.put("policeId", policeId);
        meta.put("polyline", polyline != null ? polyline : "");
        meta.put("registerTime", System.currentTimeMillis());
        redisUtil.setMap(key, meta, RedisConstant.DISPATCH_YAW_EXPIRE, TimeUnit.SECONDS);
    }

    private void updateYawCheckRoute(Long dispatchId, Long policeId, String newPolyline) {
        String key = RedisConstant.DISPATCH_YAW_CHECK_PREFIX + dispatchId + "_" + policeId;
        if (!Boolean.TRUE.equals(redisUtil.hasKey(key))) return;
        Map<String, Object> meta = redisUtil.getMap(key);
        if (meta == null) return;
        meta.put("polyline", newPolyline != null ? newPolyline : "");
        redisUtil.setMap(key, meta, RedisConstant.DISPATCH_YAW_EXPIRE, TimeUnit.SECONDS);
        log.debug("偏航检测路线已更新：dispatchId={}, policeId={}", dispatchId, policeId);
    }

    public void unregisterDispatchYawCheck(Long dispatchId, Long policeId) {
        String key = RedisConstant.DISPATCH_YAW_CHECK_PREFIX + dispatchId + "_" + policeId;
        redisUtil.delete(key);
        String countKey = "yaw_count:" + dispatchId + "_" + policeId;
        redisUtil.delete(countKey);
    }

    private int getYawCount(Long dispatchId, Long policeId) {
        String key = "yaw_count:" + dispatchId + "_" + policeId;
        String val = redisUtil.get(key);
        return val != null ? Integer.parseInt(val) : 0;
    }

    private int incrementYawCount(Long dispatchId, Long policeId) {
        String key = "yaw_count:" + dispatchId + "_" + policeId;
        int cur = getYawCount(dispatchId, policeId);
        int next = cur + 1;
        redisUtil.set(key, String.valueOf(next), RedisConstant.DISPATCH_YAW_EXPIRE, TimeUnit.SECONDS);
        return next;
    }

    private boolean canRecalcNow(Long dispatchId) {
        String lockKey = RedisConstant.DISPATCH_RECALC_LOCK_PREFIX + dispatchId;
        String lockValue = UUID.randomUUID().toString();
        return redisUtil.tryLock(lockKey, lockValue, RECALC_COOLDOWN_SECONDS, TimeUnit.SECONDS);
    }

    private List<double[]> parsePolyline(String polyline) {
        List<double[]> points = new ArrayList<>();
        if (polyline == null || polyline.isEmpty()) return points;
        try {
            String[] segments = polyline.split(";");
            for (String seg : segments) {
                String[] coords = seg.split(",");
                if (coords.length == 2) {
                    points.add(new double[]{
                            Double.parseDouble(coords[0]),
                            Double.parseDouble(coords[1])
                    });
                }
            }
        } catch (Exception e) {
            log.warn("解析路线失败：{}", e.getMessage());
        }
        return points;
    }

    @Transactional(rollbackFor = Exception.class)
    private void updateDispatchWithNewRoute(Long dispatchId, Long policeId, OfficerEtaResultDTO newEta) {
        DispatchRecord rec = dispatchRecordMapper.selectById(dispatchId);
        if (rec != null) {
            int count = rec.getYawRecalcCount() != null ? rec.getYawRecalcCount() : 0;
            rec.setYawRecalcCount(count + 1);
            rec.setLastRecalcReason("偏航自动重算");
            rec.setLastRecalcTime(LocalDateTime.now());
            if (newEta.getEtaSeconds() != null) {
                rec.setFastestEtaSeconds(newEta.getEtaSeconds());
            }
            dispatchRecordMapper.updateById(rec);
        }
    }

    private void notifyReroute(DispatchRecord dispatch, AlarmOrder alarm,
                         Long policeId, OfficerEtaResultDTO newEta) {
        Map<String, Object> data = new HashMap<>();
        data.put("dispatchId", dispatch.getId());
        data.put("dispatchNo", dispatch.getDispatchNo());
        data.put("policeId", policeId);
        data.put("alarmId", alarm.getId());
        data.put("newEta", newEta.getEtaDisplay());
        data.put("newEtaSeconds", newEta.getEtaSeconds());
        data.put("newRoute", newEta.getRoutePolyline());
        data.put("roadDistance", newEta.getRoadDistance());
        data.put("trafficLevel", newEta.getTrafficLevel());
        data.put("reason", "偏航自动重算路线");
        data.put("timestamp", System.currentTimeMillis());

        Map<String, Object> wsMsg = mqUtil.buildWebSocketMessage("dispatch_reroute", data);
        mqUtil.sendWebsocketScreenPush(wsMsg);

        Map<String, Object> mobileMsg = new HashMap<>();
        mobileMsg.put("type", "reroute");
        mobileMsg.put("dispatchNo", dispatch.getDispatchNo());
        mobileMsg.put("newRoute", newEta.getRoutePolyline());
        mobileMsg.put("eta", newEta.getEtaDisplay());
        mobileMsg.put("etaSeconds", newEta.getEtaSeconds());
        mqUtil.sendDispatchNotifyPolice(policeId, mobileMsg);
    }

    private void saveDispatchTrack(Long dispatchId, Long policeId,
                               BigDecimal lon, BigDecimal lat) {
        String listKey = RedisConstant.DISPATCH_TRACK_PREFIX + dispatchId + ":track";
        GpsLocation loc = new GpsLocation(lon, lat, LocalDateTime.now(), policeId);
        redisUtil.leftPush(listKey, JSON.toJSONString(loc));
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        try { return Long.valueOf(obj.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        try { return new BigDecimal(obj.toString()); } catch (Exception e) { return null; }
    }
}
