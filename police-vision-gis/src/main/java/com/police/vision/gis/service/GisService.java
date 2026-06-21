package com.police.vision.gis.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.enums.PoliceStatusEnum;
import com.police.vision.common.util.GpsUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.gis.entity.*;
import com.police.vision.gis.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GisService {

    private final PoliceLocationMapper policeLocationMapper;
    private final AlarmHeatmapMapper alarmHeatmapMapper;
    private final CameraPointMapper cameraPointMapper;
    private final PatrolCarMapper patrolCarMapper;
    private final MapLayerMapper mapLayerMapper;
    private final RedisUtil redisUtil;

    public List<PoliceLocation> getPoliceDistribution() {
        Set<String> keys = redisUtil.keys(RedisConstant.POLICE_LOCATION_PREFIX + "*");
        List<PoliceLocation> locations = new ArrayList<>();
        for (String key : keys) {
            PoliceLocation location = redisUtil.getObject(key, PoliceLocation.class);
            if (location != null) {
                locations.add(location);
            }
        }
        if (locations.isEmpty()) {
            locations = policeLocationMapper.selectList(null);
            for (PoliceLocation location : locations) {
                redisUtil.setObject(RedisConstant.POLICE_LOCATION_PREFIX + location.getPoliceId(),
                        location, RedisConstant.LOCATION_EXPIRE, TimeUnit.SECONDS);
            }
        }
        return locations;
    }

    public void updatePoliceLocation(Long policeId, BigDecimal lng, BigDecimal lat) {
        PoliceLocation location = policeLocationMapper.selectOne(
                new LambdaQueryWrapper<PoliceLocation>().eq(PoliceLocation::getPoliceId, policeId));
        if (location != null) {
            location.setLongitude(lng);
            location.setLatitude(lat);
            location.setTimestamp(LocalDateTime.now());
            policeLocationMapper.updateById(location);
            redisUtil.setObject(RedisConstant.POLICE_LOCATION_PREFIX + policeId,
                    location, RedisConstant.LOCATION_EXPIRE, TimeUnit.SECONDS);
            log.info("更新警员位置成功：policeId={}, lng={}, lat={}", policeId, lng, lat);
        }
    }

    public List<AlarmHeatmap> getAlarmHeatmap(String timeRange) {
        LambdaQueryWrapper<AlarmHeatmap> wrapper = new LambdaQueryWrapper<>();
        if ("today".equals(timeRange)) {
            wrapper.ge(AlarmHeatmap::getAlarmTime, LocalDateTime.now().toLocalDate().atStartOfDay());
        } else if ("week".equals(timeRange)) {
            wrapper.ge(AlarmHeatmap::getAlarmTime, LocalDateTime.now().minusWeeks(1));
        } else if ("month".equals(timeRange)) {
            wrapper.ge(AlarmHeatmap::getAlarmTime, LocalDateTime.now().minusMonths(1));
        }
        return alarmHeatmapMapper.selectList(wrapper);
    }

    public List<CameraPoint> getCameraPoints() {
        return cameraPointMapper.selectList(null);
    }

    public List<PatrolCar> getPatrolCars() {
        return patrolCarMapper.selectList(null);
    }

    public List<PoliceLocation> getNearbyPolice(BigDecimal lng, BigDecimal lat, double radiusKm) {
        List<PoliceLocation> allPolice = getPoliceDistribution();
        return allPolice.stream()
                .filter(police -> {
                    Integer status = police.getStatus();
                    return PoliceStatusEnum.getByCode(status).isAvailable();
                })
                .filter(police -> GpsUtil.isInRadius(lng, lat, police.getLongitude(), police.getLatitude(), radiusKm))
                .sorted((p1, p2) -> {
                    double d1 = GpsUtil.getDistance(lng, lat, p1.getLongitude(), p1.getLatitude());
                    double d2 = GpsUtil.getDistance(lng, lat, p2.getLongitude(), p2.getLatitude());
                    return Double.compare(d1, d2);
                })
                .collect(Collectors.toList());
    }

    public List<MapLayer> getMapLayers() {
        return mapLayerMapper.selectList(
                new LambdaQueryWrapper<MapLayer>().orderByAsc(MapLayer::getSortOrder));
    }

    public void updateLayerStatus(String layerCode, Integer visible) {
        MapLayer layer = mapLayerMapper.selectOne(
                new LambdaQueryWrapper<MapLayer>().eq(MapLayer::getLayerCode, layerCode));
        if (layer != null) {
            layer.setVisible(visible);
            mapLayerMapper.updateById(layer);
        }
    }

    public void updatePoliceLocationFromGps(GpsLocation gpsLocation) {
        if (gpsLocation == null || gpsLocation.getDeviceId() == null) {
            return;
        }
        PoliceLocation location = policeLocationMapper.selectOne(
                new LambdaQueryWrapper<PoliceLocation>().eq(PoliceLocation::getDeviceId, gpsLocation.getDeviceId()));
        if (location != null) {
            location.setLongitude(gpsLocation.getLongitude());
            location.setLatitude(gpsLocation.getLatitude());
            location.setSpeed(gpsLocation.getSpeed());
            location.setDirection(gpsLocation.getDirection());
            location.setTimestamp(gpsLocation.getTimestamp() != null ? gpsLocation.getTimestamp() : LocalDateTime.now());
            policeLocationMapper.updateById(location);
            redisUtil.setObject(RedisConstant.POLICE_LOCATION_PREFIX + location.getPoliceId(),
                    location, RedisConstant.LOCATION_EXPIRE, TimeUnit.SECONDS);
            log.info("从GPS更新警员位置成功：deviceId={}, policeId={}", gpsLocation.getDeviceId(), location.getPoliceId());
        }
    }
}
