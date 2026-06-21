package com.police.vision.gateway.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Tag(name = "大屏数据聚合", description = "指挥中心大屏统一数据接口")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardAggregationController {

    private final DiscoveryClient discoveryClient;
    private final WebClient.Builder webClientBuilder;

    @Value("${spring.application.name:police-vision-gateway}")
    private String appName;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Operation(summary = "获取大屏综合数据")
    @GetMapping("/overview")
    public Mono<Result<Map<String, Object>>> getDashboardOverview() {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("timestamp", System.currentTimeMillis());
            result.put("date", LocalDate.now().format(DATE_FORMATTER));

            CompletableFuture<Map<String, Object>> gisFuture = CompletableFuture.supplyAsync(this::fetchGisData);
            CompletableFuture<Map<String, Object>> alarmFuture = CompletableFuture.supplyAsync(this::fetchAlarmData);
            CompletableFuture<Map<String, Object>> videoFuture = CompletableFuture.supplyAsync(this::fetchVideoData);
            CompletableFuture<Map<String, Object>> statsFuture = CompletableFuture.supplyAsync(this::fetchRealTimeStats);

            try {
                Map<String, Object> gisData = gisFuture.get(5, TimeUnit.SECONDS);
                Map<String, Object> alarmData = alarmFuture.get(5, TimeUnit.SECONDS);
                Map<String, Object> videoData = videoFuture.get(5, TimeUnit.SECONDS);
                Map<String, Object> statsData = statsFuture.get(5, TimeUnit.SECONDS);

                result.put("gis", gisData);
                result.put("alarm", alarmData);
                result.put("video", videoData);
                result.put("stats", calculateCombinedStats(gisData, alarmData, videoData, statsData));
                result.put("websocketUrl", getWebSocketUrl());

                log.debug("大屏数据聚合完成");
            } catch (Exception e) {
                log.error("大屏数据聚合失败：{}", e.getMessage());
                result.put("error", e.getMessage());
            }

            return Result.success(result);
        }));
    }

    @Operation(summary = "获取实时统计数据")
    @GetMapping("/stats")
    public Mono<Result<Map<String, Object>>> getRealTimeStats() {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            try {
                return Result.success(fetchRealTimeStats());
            } catch (Exception e) {
                log.error("获取实时统计失败：{}", e.getMessage());
                return Result.fail(e.getMessage());
            }
        }));
    }

    @Operation(summary = "获取警力分布数据")
    @GetMapping("/police")
    public Mono<Result<Map<String, Object>>> getPoliceDistribution() {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            try {
                return Result.success(fetchGisData());
            } catch (Exception e) {
                log.error("获取警力分布失败：{}", e.getMessage());
                return Result.fail(e.getMessage());
            }
        }));
    }

    @Operation(summary = "获取警情统计数据")
    @GetMapping("/alarm")
    public Mono<Result<Map<String, Object>>> getAlarmStats() {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            try {
                return Result.success(fetchAlarmData());
            } catch (Exception e) {
                log.error("获取警情统计失败：{}", e.getMessage());
                return Result.fail(e.getMessage());
            }
        }));
    }

    @Operation(summary = "获取视频监控数据")
    @GetMapping("/video")
    public Mono<Result<Map<String, Object>>> getVideoStats() {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            try {
                return Result.success(fetchVideoData());
            } catch (Exception e) {
                log.error("获取视频监控数据失败：{}", e.getMessage());
                return Result.fail(e.getMessage());
            }
        }));
    }

    @Operation(summary = "获取摄像头列表")
    @GetMapping("/cameras")
    public Mono<Result<List<Map<String, Object>>>> getCameraList() {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = getServiceUrl("police-vision-video");
                if (baseUrl == null) {
                    return Result.success(Collections.emptyList());
                }

                String response = webClientBuilder.build()
                        .get()
                        .uri(baseUrl + "/api/video/camera/list?status=1")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                Result<List<Map<String, Object>>> result = JSON.parseObject(response,
                        new TypeReference<Result<List<Map<String, Object>>>>() {});

                return result;
            } catch (Exception e) {
                log.error("获取摄像头列表失败：{}", e.getMessage());
                return Result.fail(e.getMessage());
            }
        }));
    }

    @Operation(summary = "获取警情列表")
    @GetMapping("/alarms")
    public Mono<Result<List<Map<String, Object>>>> getAlarmList(
            @RequestParam(defaultValue = "1") Integer status,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = getServiceUrl("police-vision-alarm");
                if (baseUrl == null) {
                    return Result.success(Collections.emptyList());
                }

                String response = webClientBuilder.build()
                        .get()
                        .uri(baseUrl + "/api/alarm/list?status=" + status + "&limit=" + limit)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                Result<List<Map<String, Object>>> result = JSON.parseObject(response,
                        new TypeReference<Result<List<Map<String, Object>>>>() {});

                return result;
            } catch (Exception e) {
                log.error("获取警情列表失败：{}", e.getMessage());
                return Result.fail(e.getMessage());
            }
        }));
    }

    @Operation(summary = "获取告警列表")
    @GetMapping("/alerts")
    public Mono<Result<List<Map<String, Object>>>> getAlertList(
            @RequestParam(defaultValue = "0") Integer processed,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = getServiceUrl("police-vision-video");
                if (baseUrl == null) {
                    return Result.success(Collections.emptyList());
                }

                String response = webClientBuilder.build()
                        .get()
                        .uri(baseUrl + "/api/video/alert/list?processed=" + processed + "&limit=" + limit)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                Result<List<Map<String, Object>>> result = JSON.parseObject(response,
                        new TypeReference<Result<List<Map<String, Object>>>>() {});

                return result;
            } catch (Exception e) {
                log.error("获取告警列表失败：{}", e.getMessage());
                return Result.fail(e.getMessage());
            }
        }));
    }

    private Map<String, Object> fetchGisData() {
        Map<String, Object> data = new HashMap<>();
        try {
            String baseUrl = getServiceUrl("police-vision-gis");
            if (baseUrl == null) {
                return getDefaultGisData();
            }

            String policeResponse = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/api/gis/police/list")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Result<List<Map<String, Object>>> policeResult = JSON.parseObject(policeResponse,
                    new TypeReference<Result<List<Map<String, Object>>>>() {});

            String heatmapResponse = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/api/gis/heatmap")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Result<List<Map<String, Object>>> heatmapResult = JSON.parseObject(heatmapResponse,
                    new TypeReference<Result<List<Map<String, Object>>>>() {});

            String cameraResponse = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/api/gis/camera/list")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Result<List<Map<String, Object>>> cameraResult = JSON.parseObject(cameraResponse,
                    new TypeReference<Result<List<Map<String, Object>>>>() {});

            data.put("policeList", policeResult.getData() != null ? policeResult.getData() : Collections.emptyList());
            data.put("heatmapPoints", heatmapResult.getData() != null ? heatmapResult.getData() : Collections.emptyList());
            data.put("cameraList", cameraResult.getData() != null ? cameraResult.getData() : Collections.emptyList());
            data.put("policeCount", Optional.ofNullable(policeResult.getData()).map(List::size).orElse(0));
            data.put("cameraCount", Optional.ofNullable(cameraResult.getData()).map(List::size).orElse(0));

            int onlinePolice = (int) Optional.ofNullable(policeResult.getData())
                    .orElse(Collections.emptyList()).stream()
                    .filter(p -> Integer.valueOf(1).equals(p.get("status")))
                    .count();
            data.put("onlinePoliceCount", onlinePolice);

            int onlineCamera = (int) Optional.ofNullable(cameraResult.getData())
                    .orElse(Collections.emptyList()).stream()
                    .filter(c -> Integer.valueOf(1).equals(c.get("status")))
                    .count();
            data.put("onlineCameraCount", onlineCamera);

        } catch (Exception e) {
            log.error("获取GIS数据失败，使用默认数据：{}", e.getMessage());
            return getDefaultGisData();
        }
        return data;
    }

    private Map<String, Object> fetchAlarmData() {
        Map<String, Object> data = new HashMap<>();
        try {
            String baseUrl = getServiceUrl("police-vision-alarm");
            if (baseUrl == null) {
                return getDefaultAlarmData();
            }

            String statsResponse = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/api/alarm/stats/today")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Result<Map<String, Object>> statsResult = JSON.parseObject(statsResponse,
                    new TypeReference<Result<Map<String, Object>>>() {});

            if (statsResult.getData() != null) {
                data.putAll(statsResult.getData());
            }

            String typeStatsResponse = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/api/alarm/stats/type")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Result<List<Map<String, Object>>> typeResult = JSON.parseObject(typeStatsResponse,
                    new TypeReference<Result<List<Map<String, Object>>>>() {});

            data.put("typeStats", typeResult.getData() != null ? typeResult.getData() : Collections.emptyList());

        } catch (Exception e) {
            log.error("获取警情数据失败，使用默认数据：{}", e.getMessage());
            return getDefaultAlarmData();
        }
        return data;
    }

    private Map<String, Object> fetchVideoData() {
        Map<String, Object> data = new HashMap<>();
        try {
            String baseUrl = getServiceUrl("police-vision-video");
            if (baseUrl == null) {
                return getDefaultVideoData();
            }

            String statsResponse = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/api/video/alert/stats")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Result<Map<String, Object>> statsResult = JSON.parseObject(statsResponse,
                    new TypeReference<Result<Map<String, Object>>>() {});

            if (statsResult.getData() != null) {
                data.putAll(statsResult.getData());
            }

            String levelStatsResponse = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/api/video/alert/stats/level")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Result<List<Map<String, Object>>> levelResult = JSON.parseObject(levelStatsResponse,
                    new TypeReference<Result<List<Map<String, Object>>>>() {});

            data.put("levelStats", levelResult.getData() != null ? levelResult.getData() : Collections.emptyList());

        } catch (Exception e) {
            log.error("获取视频数据失败，使用默认数据：{}", e.getMessage());
            return getDefaultVideoData();
        }
        return data;
    }

    private Map<String, Object> fetchRealTimeStats() {
        Map<String, Object> data = new HashMap<>();
        try {
            String baseUrl = getServiceUrl("police-vision-gateway");
            if (baseUrl == null) {
                return getDefaultStatsData();
            }

            data.put("systemStatus", "running");
            data.put("serviceCount", discoveryClient.getServices().size());
            data.put("updateTime", System.currentTimeMillis());

        } catch (Exception e) {
            log.error("获取实时统计失败：{}", e.getMessage());
            return getDefaultStatsData();
        }
        return data;
    }

    private Map<String, Object> calculateCombinedStats(
            Map<String, Object> gis, Map<String, Object> alarm,
            Map<String, Object> video, Map<String, Object> stats) {

        Map<String, Object> combined = new LinkedHashMap<>();

        combined.put("keyIndicators", Arrays.asList(
                createIndicator("今日警情", getNumber(alarm, "totalToday", 0), "alarm", "#1890ff"),
                createIndicator("待处理", getNumber(alarm, "pending", 0), "pending", "#faad14"),
                createIndicator("已处理", getNumber(alarm, "completedToday", 0), "completed", "#52c41a"),
                createIndicator("AI告警", getNumber(video, "todayAlertCount", 0), "alert", "#ff4d4f"),
                createIndicator("在线警力", getNumber(gis, "onlinePoliceCount", 0), "police", "#13c2c2"),
                createIndicator("在线监控", getNumber(gis, "onlineCameraCount", 0), "camera", "#722ed1")
        ));

        combined.put("alarmTrend", generateTrendData());
        combined.put("alertTrend", generateAlertTrend());
        combined.put("hourlyDistribution", generateHourlyData());
        combined.put("areaDistribution", generateAreaData());

        return combined;
    }

    private Map<String, Object> createIndicator(String name, Number value, String type, String color) {
        Map<String, Object> indicator = new LinkedHashMap<>();
        indicator.put("name", name);
        indicator.put("value", value);
        indicator.put("type", type);
        indicator.put("color", color);
        indicator.put("trend", Math.random() > 0.5 ? "up" : "down");
        indicator.put("trendValue", String.format("%.1f%%", Math.random() * 20));
        return indicator;
    }

    private long getNumber(Map<String, Object> map, String key, long defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private List<Map<String, Object>> generateTrendData() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", (24 - i) + "时");
            item.put("value", (int) (Math.random() * 20 + 5));
            data.add(item);
        }
        return data;
    }

    private List<Map<String, Object>> generateAlertTrend() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", (24 - i) + "时");
            item.put("face", (int) (Math.random() * 10));
            item.put("plate", (int) (Math.random() * 8));
            item.put("behavior", (int) (Math.random() * 5));
            data.add(item);
        }
        return data;
    }

    private List<Map<String, Object>> generateHourlyData() {
        List<Map<String, Object>> data = new ArrayList<>();
        int[] hours = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
        for (int hour : hours) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", hour + ":00");
            item.put("count", (int) (Math.random() * 30 + 10));
            data.add(item);
        }
        return data;
    }

    private List<Map<String, Object>> generateAreaData() {
        String[] areas = {"东城区", "西城区", "朝阳区", "海淀区", "丰台区", "石景山区"};
        List<Map<String, Object>> data = new ArrayList<>();
        for (String area : areas) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", area);
            item.put("value", (int) (Math.random() * 50 + 20));
            data.add(item);
        }
        return data;
    }

    private Map<String, Object> getDefaultGisData() {
        Map<String, Object> data = new HashMap<>();
        data.put("policeList", Collections.emptyList());
        data.put("heatmapPoints", Collections.emptyList());
        data.put("cameraList", Collections.emptyList());
        data.put("policeCount", 0);
        data.put("cameraCount", 0);
        data.put("onlinePoliceCount", 0);
        data.put("onlineCameraCount", 0);
        return data;
    }

    private Map<String, Object> getDefaultAlarmData() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalToday", 0);
        data.put("pending", 0);
        data.put("processing", 0);
        data.put("completedToday", 0);
        data.put("typeStats", Collections.emptyList());
        return data;
    }

    private Map<String, Object> getDefaultVideoData() {
        Map<String, Object> data = new HashMap<>();
        data.put("todayAlertCount", 0);
        data.put("pendingAlertCount", 0);
        data.put("levelStats", Collections.emptyList());
        return data;
    }

    private Map<String, Object> getDefaultStatsData() {
        Map<String, Object> data = new HashMap<>();
        data.put("systemStatus", "unknown");
        data.put("serviceCount", 0);
        data.put("updateTime", System.currentTimeMillis());
        return data;
    }

    private String getServiceUrl(String serviceName) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            if (instances == null || instances.isEmpty()) {
                log.warn("服务未发现：{}", serviceName);
                return null;
            }
            ServiceInstance instance = instances.get(0);
            return instance.getUri().toString();
        } catch (Exception e) {
            log.warn("获取服务地址失败：{}", serviceName);
            return null;
        }
    }

    private String getWebSocketUrl() {
        return "ws://" + System.getenv().getOrDefault("SERVER_HOST", "localhost") + ":8085/ws/screen";
    }
}
