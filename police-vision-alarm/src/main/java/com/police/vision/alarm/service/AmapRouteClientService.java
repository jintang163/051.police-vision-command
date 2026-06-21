package com.police.vision.alarm.service;

import com.alibaba.fastjson2.JSON;
import com.police.vision.alarm.config.AmapConfig;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.AmapRouteResultDTO;
import com.police.vision.common.dto.AmapTrafficStatusDTO;
import com.police.vision.common.dto.OfficerEtaResultDTO;
import com.police.vision.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmapRouteClientService {

    private final AmapConfig amapConfig;
    private final RedisUtil redisUtil;

    private CloseableHttpClient httpClient;

    private final Map<String, Long> trafficRefreshMap = new ConcurrentHashMap<>();

    private static final double EARTH_RADIUS_KM = 6371.0088;

    @PostConstruct
    public void init() {
        try {
            httpClient = HttpClients.custom()
                    .setConnectionTimeToLive(amapConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                    .build();
            log.info("高德API客户端初始化成功，enabled={}, mock={}", amapConfig.isEnabled(), amapConfig.isMockEnabled());
        } catch (Exception e) {
            log.error("高德API客户端初始化失败：{}", e.getMessage());
        }
    }

    public AmapRouteResultDTO calculateRoute(BigDecimal originLon, BigDecimal originLat,
                                             BigDecimal destLon, BigDecimal destLat) {
        if (!amapConfig.isEnabled() || amapConfig.isMockEnabled()) {
            return buildMockRoute(originLon, originLat, destLon, destLat);
        }

        String cacheKey = buildRouteCacheKey(originLon, originLat, destLon, destLat);
        AmapRouteResultDTO cached = redisUtil.getObject(cacheKey, AmapRouteResultDTO.class);
        if (cached != null) {
            log.debug("命中路线缓存：{}", cacheKey);
            return cached;
        }

        try {
            String origin = formatCoord(originLon, originLat);
            String destination = formatCoord(destLon, destLat);
            String url = String.format("%s%s?key=%s&origin=%s&destination=%s&strategy=%s&extensions=%s",
                    amapConfig.getBaseUrl(),
                    amapConfig.getDrivingRoutePath(),
                    amapConfig.getKey(),
                    URLEncoder.encode(origin, StandardCharsets.UTF_8),
                    URLEncoder.encode(destination, StandardCharsets.UTF_8),
                    amapConfig.getStrategy(),
                    amapConfig.getExtensions()
            );

            String response = doGet(url);
            AmapRouteResultDTO result = JSON.parseObject(response, AmapRouteResultDTO.class);

            if (result != null && result.getStatus() != null && result.getStatus() == 1) {
                redisUtil.setObject(cacheKey, result, RedisConstant.ROUTE_ETA_EXPIRE, TimeUnit.SECONDS);
            } else {
                log.warn("高德路线API返回异常：status={}, info={}",
                        result != null ? result.getStatus() : "null",
                        result != null ? result.getInfo() : "null");
                return buildMockRoute(originLon, originLat, destLon, destLat);
            }
            return result;
        } catch (Exception e) {
            log.error("调用高德路线API失败：{}", e.getMessage());
            return buildMockRoute(originLon, originLat, destLon, destLat);
        }
    }

    public AmapTrafficStatusDTO getTrafficAround(BigDecimal centerLon, BigDecimal centerLat, int radiusMeters) {
        if (!amapConfig.isEnabled() || amapConfig.isMockEnabled()) {
            return buildMockTrafficStatus();
        }

        String gridKey = buildTrafficGridKey(centerLon, centerLat);
        long now = System.currentTimeMillis();
        Long lastRefresh = trafficRefreshMap.get(gridKey);
        if (lastRefresh != null && (now - lastRefresh) < amapConfig.getTrafficRefreshMinutes() * 60 * 1000L) {
            String cacheKey = RedisConstant.ROAD_TRAFFIC_PREFIX + gridKey;
            AmapTrafficStatusDTO cached = redisUtil.getObject(cacheKey, AmapTrafficStatusDTO.class);
            if (cached != null) return cached;
        }

        try {
            String location = formatCoord(centerLon, centerLat);
            String url = String.format("%s%s?key=%s&location=%s&radius=%d&level=5&extensions=%s",
                    amapConfig.getBaseUrl(),
                    amapConfig.getTrafficCirclePath(),
                    amapConfig.getKey(),
                    URLEncoder.encode(location, StandardCharsets.UTF_8),
                    radiusMeters,
                    "base"
            );

            String response = doGet(url);
            AmapTrafficStatusDTO result = JSON.parseObject(response, AmapTrafficStatusDTO.class);

            if (result != null && result.getStatus() != null && result.getStatus() == 1) {
                trafficRefreshMap.put(gridKey, now);
                String cacheKey = RedisConstant.ROAD_TRAFFIC_PREFIX + gridKey;
                redisUtil.setObject(cacheKey, result, RedisConstant.ROAD_TRAFFIC_EXPIRE, TimeUnit.SECONDS);
            } else {
                log.warn("高德路况API返回异常");
                return buildMockTrafficStatus();
            }
            return result;
        } catch (Exception e) {
            log.error("调用高德路况API失败：{}", e.getMessage());
            return buildMockTrafficStatus();
        }
    }

    public OfficerEtaResultDTO calculateOfficerEta(Long policeId, String policeName,
                                                   BigDecimal officerLon, BigDecimal officerLat,
                                                   BigDecimal alarmLon, BigDecimal alarmLat) {
        double straightDistanceKm = calculateStraightDistanceKm(officerLon, officerLat, alarmLon, alarmLat);
        BigDecimal straightDistance = BigDecimal.valueOf(straightDistanceKm)
                .setScale(3, RoundingMode.HALF_UP);

        AmapRouteResultDTO routeResult = calculateRoute(officerLon, officerLat, alarmLon, alarmLat);

        Integer duration = routeResult.getFirstPathDuration();
        BigDecimal roadDist = routeResult.getFirstPathDistance();
        double trafficLevel = routeResult.getAvgTrafficLevel();

        if (duration == null || duration <= 0) {
            int baseEta = (int) (straightDistanceKm * 180);
            duration = Math.max(60, baseEta);
        }
        if (roadDist == null) {
            roadDist = BigDecimal.valueOf(straightDistanceKm * 1000);
        }

        String polyline = "";
        String roads = "";
        Integer trafficLights = 0;
        if (routeResult.getRoute() != null && !routeResult.getRoute().getPaths().isEmpty()) {
            AmapRouteResultDTO.PathInfo path = routeResult.getRoute().getPaths().get(0);
            polyline = extractPolyline(path);
            roads = extractRoadNames(path);
            try {
                if (path.getTrafficLights() != null) {
                    trafficLights = Integer.parseInt(path.getTrafficLights());
                }
            } catch (Exception ignored) {}
        }

        BigDecimal avgSpeed = BigDecimal.ZERO;
        if (roadDist != null && duration != null && duration > 0) {
            double km = roadDist.doubleValue() / 1000.0;
            double hours = duration / 3600.0;
            if (hours > 0) {
                avgSpeed = BigDecimal.valueOf(km / hours).setScale(2, RoundingMode.HALF_UP);
            }
        }

        return OfficerEtaResultDTO.builder()
                .policeId(policeId)
                .policeName(policeName)
                .officerLongitude(officerLon)
                .officerLatitude(officerLat)
                .alarmLongitude(alarmLon)
                .alarmLatitude(alarmLat)
                .straightDistance(straightDistance)
                .roadDistance(roadDist)
                .etaSeconds(duration)
                .trafficLevel((int) Math.round(trafficLevel))
                .routePolyline(polyline)
                .routeName(roads)
                .strategy(amapConfig.getStrategy())
                .trafficLightsCount(trafficLights)
                .avgSpeed(avgSpeed)
                .roadNames(roads)
                .calculateTime(LocalDateTime.now())
                .calculateVersion(1)
                .build();
    }

    public static double calculateStraightDistanceKm(BigDecimal lon1, BigDecimal lat1,
                                                      BigDecimal lon2, BigDecimal lat2) {
        if (lon1 == null || lat1 == null || lon2 == null || lat2 == null) return 0;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static BigDecimal calculatePointToLineDistanceMeters(BigDecimal px, BigDecimal py,
                                                                 BigDecimal ax, BigDecimal ay,
                                                                 BigDecimal bx, BigDecimal by) {
        if (px == null || py == null || ax == null || ay == null || bx == null || by == null) {
            return null;
        }
        double x = px.doubleValue(), y = py.doubleValue();
        double x1 = ax.doubleValue(), y1 = ay.doubleValue();
        double x2 = bx.doubleValue(), y2 = by.doubleValue();

        double dx = x2 - x1, dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            double km = calculateStraightDistanceKm(px, py, ax, ay);
            return BigDecimal.valueOf(km * 1000).setScale(2, RoundingMode.HALF_UP);
        }

        double t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;

        double km = calculateStraightDistanceKm(
                BigDecimal.valueOf(x), BigDecimal.valueOf(y),
                BigDecimal.valueOf(projX), BigDecimal.valueOf(projY)
        );
        return BigDecimal.valueOf(km * 1000).setScale(2, RoundingMode.HALF_UP);
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanupExpiredTrafficCache() {
        long now = System.currentTimeMillis();
        long expireMs = amapConfig.getTrafficRefreshMinutes() * 60 * 1000L;
        trafficRefreshMap.entrySet().removeIf(e -> (now - e.getValue()) > expireMs);
        if (log.isDebugEnabled()) {
            log.debug("清理过期路况缓存，剩余条数：{}", trafficRefreshMap.size());
        }
    }

    private String doGet(String url) throws Exception {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) init();
            }
        }
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Connection", "keep-alive");
        return httpClient.execute(httpGet, response ->
                EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
    }

    private String buildRouteCacheKey(BigDecimal oLon, BigDecimal oLat, BigDecimal dLon, BigDecimal dLat) {
        return String.format("%s%s_%s_%s_%s_%s",
                RedisConstant.ROUTE_ETA_CACHE_PREFIX,
                roundCoord(oLon), roundCoord(oLat),
                roundCoord(dLon), roundCoord(dLat));
    }

    private String buildTrafficGridKey(BigDecimal lon, BigDecimal lat) {
        double gridSize = 0.01;
        long gridX = (long) Math.floor(lon.doubleValue() / gridSize);
        long gridY = (long) Math.floor(lat.doubleValue() / gridSize);
        return gridX + "_" + gridY;
    }

    private String formatCoord(BigDecimal lon, BigDecimal lat) {
        return lon.setScale(6, RoundingMode.HALF_UP) + "," + lat.setScale(6, RoundingMode.HALF_UP);
    }

    private String roundCoord(BigDecimal coord) {
        return coord.setScale(3, RoundingMode.HALF_UP).toString();
    }

    private String extractPolyline(AmapRouteResultDTO.PathInfo path) {
        if (path.getSteps() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (AmapRouteResultDTO.StepInfo step : path.getSteps()) {
            if (step.getPolyline() != null) {
                if (sb.length() > 0) sb.append(";");
                sb.append(step.getPolyline());
            }
        }
        return sb.toString();
    }

    private String extractRoadNames(AmapRouteResultDTO.PathInfo path) {
        if (path.getSteps() == null) return "";
        Set<String> roads = new LinkedHashSet<>();
        for (AmapRouteResultDTO.StepInfo step : path.getSteps()) {
            if (step.getRoad() != null && !step.getRoad().isEmpty()) {
                roads.add(step.getRoad());
            }
        }
        return String.join("→", roads);
    }

    private AmapRouteResultDTO buildMockRoute(BigDecimal oLon, BigDecimal oLat,
                                               BigDecimal dLon, BigDecimal dLat) {
        AmapRouteResultDTO result = new AmapRouteResultDTO();
        result.setStatus(1);
        result.setInfo("OK");
        double km = calculateStraightDistanceKm(oLon, oLat, dLon, dLat);
        int meters = (int) (km * 1300);
        int baseSeconds = (int) (km * 180);
        Random r = new Random();
        int trafficFactor = 1 + r.nextInt(40) / 100;
        int duration = Math.max(60, baseSeconds * trafficFactor);

        AmapRouteResultDTO.RouteInfo routeInfo = new AmapRouteResultDTO.RouteInfo();
        routeInfo.setOrigin(formatCoord(oLon, oLat));
        routeInfo.setDestination(formatCoord(dLon, dLat));

        AmapRouteResultDTO.PathInfo pathInfo = new AmapRouteResultDTO.PathInfo();
        pathInfo.setDistance(String.valueOf(meters));
        pathInfo.setDuration(String.valueOf(duration));
        pathInfo.setStrategy(amapConfig.getStrategy());
        pathInfo.setTrafficLights(String.valueOf(r.nextInt(10)));
        pathInfo.setSteps(new ArrayList<>());
        pathInfo.setTmcs(new ArrayList<>());

        int segCount = 3 + r.nextInt(4);
        for (int i = 0; i < segCount; i++) {
            AmapRouteResultDTO.TmcInfo tmc = new AmapRouteResultDTO.TmcInfo();
            tmc.setDistance(String.valueOf(meters / segCount));
            tmc.setStatus(String.valueOf(r.nextInt(5)));
            tmc.setPolyline("");
            pathInfo.getTmcs().add(tmc);
        }

        routeInfo.setPaths(Collections.singletonList(pathInfo));
        result.setRoute(routeInfo);
        return result;
    }

    private AmapTrafficStatusDTO buildMockTrafficStatus() {
        AmapTrafficStatusDTO result = new AmapTrafficStatusDTO();
        result.setStatus(1);
        result.setInfo("OK");
        AmapTrafficStatusDTO.TrafficEvaluation eval = new AmapTrafficStatusDTO.TrafficEvaluation();
        Random r = new Random();
        eval.setStatus(String.valueOf(r.nextInt(4) + 1));
        eval.setDescription("路况畅通");
        eval.setExpedite(String.valueOf(60 + r.nextInt(30)));
        eval.setCongested(String.valueOf(r.nextInt(20)));
        eval.setBlocked(String.valueOf(r.nextInt(10)));
        eval.setRoads(new ArrayList<>());
        result.setEvaluation(eval);
        return result;
    }
}
