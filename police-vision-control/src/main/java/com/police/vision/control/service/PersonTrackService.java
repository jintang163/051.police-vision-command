package com.police.vision.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.control.entity.PersonTrackPoint;
import com.police.vision.control.entity.TargetPerson;
import com.police.vision.control.mapper.PersonTrackPointMapper;
import com.police.vision.control.mapper.TargetPersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonTrackService {

    private final PersonTrackPointMapper trackPointMapper;
    private final TargetPersonMapper targetPersonMapper;

    public List<PersonTrackPoint> addTrackPoint(String personId, BigDecimal longitude, BigDecimal latitude,
                                                BigDecimal speed, BigDecimal direction,
                                                String sourceType, String deviceId) {
        TargetPerson person = targetPersonMapper.selectByPersonId(personId);
        if (person == null) {
            return Collections.emptyList();
        }
        PersonTrackPoint point = new PersonTrackPoint();
        point.setTrackId(UUID.randomUUID().toString().replace("-", ""));
        point.setPersonId(personId);
        point.setPersonName(person.getPersonName());
        point.setLongitude(longitude);
        point.setLatitude(latitude);
        point.setSpeed(speed);
        point.setDirection(direction);
        point.setSourceType(sourceType);
        point.setDeviceId(deviceId);
        point.setGpsTime(LocalDateTime.now());
        point.setLocationType("GPS");
        trackPointMapper.insert(point);

        person.setLongitude(longitude);
        person.setLatitude(latitude);
        targetPersonMapper.updateById(person);
        return Collections.singletonList(point);
    }

    public List<PersonTrackPoint> getPersonTrackHistory(String personId, LocalDateTime startTime, LocalDateTime endTime) {
        return trackPointMapper.selectByPersonIdAndTimeRange(personId, startTime, endTime);
    }

    public List<PersonTrackPoint> getRecentTrack(String personId, int days, int limit) {
        return trackPointMapper.selectRecentByPersonId(personId, days, limit);
    }

    public List<GpsLocation> getRealtimeLocations(List<String> personIds, int minutes) {
        List<PersonTrackPoint> points = trackPointMapper.selectRealtimeByPersonIds(personIds, minutes);
        Map<String, PersonTrackPoint> latestMap = new LinkedHashMap<>();
        for (PersonTrackPoint p : points) {
            if (!latestMap.containsKey(p.getPersonId())) {
                latestMap.put(p.getPersonId(), p);
            } else {
                PersonTrackPoint existing = latestMap.get(p.getPersonId());
                if (p.getGpsTime().isAfter(existing.getGpsTime())) {
                    latestMap.put(p.getPersonId(), p);
                }
            }
        }
        return latestMap.values().stream()
                .map(p -> new GpsLocation(p.getLongitude(), p.getLatitude(), p.getGpsTime(), null))
                .collect(Collectors.toList());
    }

    public Map<String, Object> analyzeActivityPattern(String personId, int days) {
        List<PersonTrackPoint> points = getRecentTrack(personId, days, 10000);
        Map<String, Object> result = new LinkedHashMap<>();
        if (points.isEmpty()) {
            result.put("sampleCount", 0);
            result.put("hotspots", Collections.emptyList());
            result.put("timeDistribution", Collections.emptyMap());
            return result;
        }
        Map<String, Long> timeDistribution = new LinkedHashMap<>();
        timeDistribution.put("morning", 0L);
        timeDistribution.put("afternoon", 0L);
        timeDistribution.put("evening", 0L);
        timeDistribution.put("night", 0L);

        Map<String, long[]> gridCount = new LinkedHashMap<>();
        for (PersonTrackPoint p : points) {
            int hour = p.getGpsTime().getHour();
            if (hour >= 6 && hour < 12) {
                timeDistribution.merge("morning", 1L, Long::sum);
            } else if (hour >= 12 && hour < 18) {
                timeDistribution.merge("afternoon", 1L, Long::sum);
            } else if (hour >= 18 && hour < 22) {
                timeDistribution.merge("evening", 1L, Long::sum);
            } else {
                timeDistribution.merge("night", 1L, Long::sum);
            }
            String gridKey = String.format("%.3f_%.3f",
                    Math.floor(p.getLongitude().doubleValue() * 100) / 100.0,
                    Math.floor(p.getLatitude().doubleValue() * 100) / 100.0);
            gridCount.computeIfAbsent(gridKey, k -> new long[]{0})[0]++;
        }

        List<Map<String, Object>> hotspots = gridCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    String[] parts = e.getKey().split("_");
                    Map<String, Object> hp = new LinkedHashMap<>();
                    hp.put("longitude", Double.parseDouble(parts[0]));
                    hp.put("latitude", Double.parseDouble(parts[1]));
                    hp.put("count", e.getValue()[0]);
                    hp.put("ratio", (double) e.getValue()[0] / points.size());
                    return hp;
                })
                .collect(Collectors.toList());

        result.put("sampleCount", points.size());
        result.put("hotspots", hotspots);
        result.put("timeDistribution", timeDistribution);
        return result;
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchImportTrackPoints(List<Map<String, Object>> trackDataList) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (trackDataList == null || trackDataList.isEmpty()) {
            result.put("success", false);
            result.put("message", "数据为空");
            result.put("imported", 0);
            return result;
        }

        List<PersonTrackPoint> points = new ArrayList<>();
        Set<String> personIds = new HashSet<>();
        int batchSize = 1000;
        int totalImported = 0;

        for (Map<String, Object> data : trackDataList) {
            try {
                String personId = (String) data.get("personId");
                if (personId == null || personId.isEmpty()) continue;

                TargetPerson person = targetPersonMapper.selectByPersonId(personId);
                if (person == null) continue;

                PersonTrackPoint point = new PersonTrackPoint();
                point.setTrackId(UUID.randomUUID().toString().replace("-", ""));
                point.setPersonId(personId);
                point.setPersonName(person.getPersonName());
                point.setLongitude(new BigDecimal(data.get("longitude").toString()));
                point.setLatitude(new BigDecimal(data.get("latitude").toString()));
                point.setSpeed(data.get("speed") != null ? new BigDecimal(data.get("speed").toString()) : null);
                point.setDirection(data.get("direction") != null ? new BigDecimal(data.get("direction").toString()) : null);
                point.setSourceType(data.get("sourceType") != null ? (String) data.get("sourceType") : "GPS");
                point.setDeviceId(data.get("deviceId") != null ? (String) data.get("deviceId") : null);
                point.setLocationType(data.get("locationType") != null ? (String) data.get("locationType") : "GPS");

                Object gpsTimeObj = data.get("gpsTime");
                if (gpsTimeObj instanceof LocalDateTime) {
                    point.setGpsTime((LocalDateTime) gpsTimeObj);
                } else if (gpsTimeObj != null) {
                    point.setGpsTime(LocalDateTime.parse(gpsTimeObj.toString()));
                } else {
                    point.setGpsTime(LocalDateTime.now());
                }

                points.add(point);
                personIds.add(personId);

                if (points.size() >= batchSize) {
                    totalImported += trackPointMapper.batchInsertTrackPoints(points);
                    points.clear();
                }
            } catch (Exception e) {
                log.warn("导入轨迹点失败，跳过: {}", e.getMessage());
            }
        }

        if (!points.isEmpty()) {
            totalImported += trackPointMapper.batchInsertTrackPoints(points);
        }

        for (String personId : personIds) {
            try {
                LambdaQueryWrapper<PersonTrackPoint> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(PersonTrackPoint::getPersonId, personId);
                wrapper.orderByDesc(PersonTrackPoint::getGpsTime);
                wrapper.last("limit 1");
                PersonTrackPoint latest = trackPointMapper.selectOne(wrapper);
                if (latest != null) {
                    TargetPerson person = targetPersonMapper.selectByPersonId(personId);
                    if (person != null) {
                        person.setLongitude(latest.getLongitude());
                        person.setLatitude(latest.getLatitude());
                        targetPersonMapper.updateById(person);
                    }
                }
            } catch (Exception e) {
                log.warn("更新人员最新位置失败: personId={}", personId, e);
            }
        }

        int cleaned = trackPointMapper.cleanOldTrackPoints(90, 10000);

        result.put("success", true);
        result.put("imported", totalImported);
        result.put("uniquePersons", personIds.size());
        result.put("cleanedOld", cleaned);
        result.put("message", "成功导入" + totalImported + "条轨迹数据，涉及" + personIds.size() + "名重点人员");
        log.info("批量导入轨迹数据完成: imported={}, persons={}", totalImported, personIds.size());
        return result;
    }

    public Map<String, Object> getTrackDatabaseStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long totalPoints = trackPointMapper.selectCount(null);
        stats.put("totalPoints", totalPoints);

        List<Map<String, Object>> personStats = trackPointMapper.getTrackStatistics("ALL", 90);
        stats.put("personStats", personStats);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime ninetyDaysAgo = now.minusDays(90);
        LambdaQueryWrapper<PersonTrackPoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(PersonTrackPoint::getGpsTime, ninetyDaysAgo);
        long pointsIn90Days = trackPointMapper.selectCount(wrapper);
        stats.put("pointsIn90Days", pointsIn90Days);
        stats.put("dataCoverage", totalPoints > 0 ? (double) pointsIn90Days / totalPoints : 0);

        return stats;
    }
}
