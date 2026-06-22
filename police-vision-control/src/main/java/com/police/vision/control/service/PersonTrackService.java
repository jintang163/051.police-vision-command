package com.police.vision.control.service;

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
}
