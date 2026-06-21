package com.police.vision.flink.job;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.entity.TrafficCaptureData;
import com.police.vision.flink.config.FlinkConfig;
import com.police.vision.flink.entity.VehicleFollowResult;
import com.police.vision.flink.schema.TrafficCaptureSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleFollowDetectionJob implements CommandLineRunner {

    private final FlinkConfig flinkConfig;

    public static final int FOLLOW_VEHICLE_THRESHOLD = 3;
    public static final int WINDOW_MINUTES = 15;
    public static final int SLIDE_MINUTES = 3;
    public static final int COOLDOWN_MINUTES = 30;
    public static final int ADJACENT_MAX_SECONDS = 180;
    public static final int MIN_ROUTE_SEGMENT_COUNT = 2;
    public static final long TRACK_TTL_SECONDS = 30 * 60L;

    private static final Set<String> ALERT_COOLDOWN = ConcurrentHashMap.newKeySet();

    public void runJob() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        KafkaSource<TrafficCaptureData> kafkaSource = KafkaSource.<TrafficCaptureData>builder()
                .setBootstrapServers(flinkConfig.getKafka().getBootstrapServers())
                .setTopics(flinkConfig.getKafka().getTrafficCaptureTopic())
                .setGroupId("flink-follow-detection-v2-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TrafficCaptureSchema())
                .build();

        DataStream<TrafficCaptureData> events = env
                .fromSource(kafkaSource, WatermarkStrategy
                                .<TrafficCaptureData>forBoundedOutOfOrderness(Duration.ofSeconds(30))
                                .withTimestampAssigner((event, ts) -> event.getEventTimestamp()),
                        "TrafficCaptureSource"
                )
                .name("TrafficCaptureKafkaSource-Follow")
                .filter(e -> e.getPlateNo() != null && !e.getPlateNo().isEmpty()
                        && e.getCrossingId() != null && e.getCaptureTime() != null)
                .name("FilterValidEvents");

        // ========== 模型1：相邻过车时间差（单卡口短时间多车） ==========
        KeyedStream<TrafficCaptureData, String> crossingKeyed = events
                .keyBy(event -> event.getCrossingId() + "_" + (event.getDirection() != null ? event.getDirection() : 0));

        SingleOutputStreamOperator<VehicleFollowResult> adjacentStream = crossingKeyed
                .window(SlidingEventTimeWindows.of(
                        Time.minutes(WINDOW_MINUTES), Time.minutes(SLIDE_MINUTES)))
                .process(new AdjacentTimeWindowFunction())
                .filter(Objects::nonNull)
                .name("AdjacentTimeDiffDetection");

        // ========== 模型2：跨卡口轨迹序列（同路线多车） ==========
        KeyedStream<TrafficCaptureData, String> plateKeyed = events
                .keyBy(TrafficCaptureData::getPlateNo);

        DataStream<Tuple2<String, List<TrafficCaptureData>>> vehicleTrackStream = plateKeyed
                .process(new VehicleTrackBuildFunction())
                .filter(t -> t.f1 != null && t.f1.size() >= MIN_ROUTE_SEGMENT_COUNT)
                .name("BuildVehicleTracks");

        DataStream<VehicleFollowResult> routeStream = vehicleTrackStream
                .keyBy(t -> buildRouteKey(t.f1))
                .process(new RouteMatchFunction())
                .filter(Objects::nonNull)
                .name("RouteSequenceMatchDetection");

        // ========== 合并两路检测结果，统一去重和冷却 ==========
        DataStream<VehicleFollowResult> mergedStream = adjacentStream.union(routeStream);

        DataStream<VehicleFollowResult> alertStream = mergedStream
                .keyBy(r -> r.getCrossingId() + "_" + r.getPlateNos().hashCode())
                .process(new FollowCooldownFunction())
                .name("CooldownAndDedup");

        alertStream.addSink(new FollowAlertRocketMQSink(flinkConfig))
                .name("PushToRocketMQ");
        alertStream.addSink(new FollowAlertWebSocketSink())
                .name("PushToWebSocket");

        env.executeAsync("VehicleFollowDetection-Job-V2");
        log.info("Flink跟车分析任务V2已启动：阈值={}辆，窗口={}分钟，时间差阈值={}秒，最小轨迹段={}",
                FOLLOW_VEHICLE_THRESHOLD, WINDOW_MINUTES, ADJACENT_MAX_SECONDS, MIN_ROUTE_SEGMENT_COUNT);
    }

    private static String buildRouteKey(List<TrafficCaptureData> track) {
        return track.stream()
                .sorted(Comparator.comparing(TrafficCaptureData::getCaptureTime))
                .map(t -> t.getCrossingId())
                .collect(Collectors.joining("->"));
    }

    @Override
    public void run(String... args) {
        try {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(15000L);
                    runJob();
                } catch (Exception e) {
                    log.error("启动Flink跟车分析任务失败：{}", e.getMessage(), e);
                }
            });
            thread.setDaemon(true);
            thread.setName("Flink-Vehicle-Follow-Detection");
            thread.start();
        } catch (Exception e) {
            log.warn("Flink跟车分析任务启动线程创建失败：{}", e.getMessage());
        }
    }

    // ==============================================================
    // 模型1：相邻过车时间差窗口函数
    // 思路：同一卡口同一方向，按过车时间排序，相邻车时间差<ADJACENT_MAX_SECONDS
    // 且连续>=THRESHOLD辆车，判定为跟车
    // ==============================================================
    public static class AdjacentTimeWindowFunction
            extends ProcessWindowFunction<TrafficCaptureData, VehicleFollowResult, String, TimeWindow> {

        @Override
        public void process(String key, Context context,
                            Iterable<TrafficCaptureData> elements,
                            Collector<VehicleFollowResult> out) {
            List<TrafficCaptureData> sortedList = StreamSupport.stream(elements.spliterator(), false)
                    .sorted(Comparator.comparing(TrafficCaptureData::getCaptureTime))
                    .collect(Collectors.toList());

            if (sortedList.size() < FOLLOW_VEHICLE_THRESHOLD) {
                return;
            }

            int i = 0;
            while (i < sortedList.size() - 1) {
                List<TrafficCaptureData> group = new ArrayList<>();
                group.add(sortedList.get(i));

                int j = i + 1;
                while (j < sortedList.size()) {
                    TrafficCaptureData prev = sortedList.get(j - 1);
                    TrafficCaptureData curr = sortedList.get(j);

                    long diffSeconds = Duration.between(prev.getCaptureTime(), curr.getCaptureTime()).getSeconds();
                    if (diffSeconds >= 0 && diffSeconds <= ADJACENT_MAX_SECONDS) {
                        if (!prev.getPlateNo().equals(curr.getPlateNo())) {
                            group.add(curr);
                        }
                        j++;
                    } else {
                        break;
                    }
                }

                long uniqueCount = group.stream().map(TrafficCaptureData::getPlateNo).distinct().count();
                if (uniqueCount >= FOLLOW_VEHICLE_THRESHOLD) {
                    VehicleFollowResult result = buildResultFromGroup(group, key);
                    if (result != null) {
                        out.collect(result);
                        log.debug("【相邻时间差模型】命中跟车：key={}, count={}, plates={}",
                                key, uniqueCount, result.getPlateNos());
                    }
                }
                i = Math.max(i + 1, j - 1);
            }
        }

        private VehicleFollowResult buildResultFromGroup(List<TrafficCaptureData> group, String key) {
            if (group == null || group.isEmpty()) return null;

            List<String> plateNos = group.stream()
                    .map(TrafficCaptureData::getPlateNo)
                    .distinct()
                    .collect(Collectors.toList());

            if (plateNos.size() < FOLLOW_VEHICLE_THRESHOLD) return null;

            TrafficCaptureData first = group.get(0);
            TrafficCaptureData last = group.get(group.size() - 1);

            VehicleFollowResult result = new VehicleFollowResult();
            result.setAlertId("VF-ADJ-" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
            result.setAlertNo("VF-ADJ-" + System.currentTimeMillis());
            result.setCrossingId(first.getCrossingId());
            result.setCrossingName(first.getCrossingName());
            result.setRoadDirection(first.getDirection() != null ? String.valueOf(first.getDirection()) : "");
            result.setVehicleCount(plateNos.size());
            result.setPlateNos(plateNos);

            Set<String> vehicleTypes = group.stream()
                    .map(TrafficCaptureData::getVehicleType)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            result.setVehicleTypes(new ArrayList<>(vehicleTypes));

            OptionalDouble avgSpeed = group.stream()
                    .map(TrafficCaptureData::getSpeed)
                    .filter(Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average();
            if (avgSpeed.isPresent()) {
                result.setAverageSpeed(BigDecimal.valueOf(avgSpeed.getAsDouble()).setScale(2, RoundingMode.HALF_UP));
            }

            result.setWindowStart(first.getCaptureTime());
            result.setWindowEnd(last.getCaptureTime());

            int level;
            if (plateNos.size() >= 10) level = 1;
            else if (plateNos.size() >= 5) level = 2;
            else level = 3;
            result.setAlertLevel(level);

            result.setDescription(String.format("跟车告警[相邻时间差模型]：卡口[%s]在%s秒内连续有%d辆车通过（相邻时间差≤%ds）",
                    first.getCrossingName(),
                    Duration.between(first.getCaptureTime(), last.getCaptureTime()).getSeconds(),
                    plateNos.size(), ADJACENT_MAX_SECONDS));
            result.setTimestamp(System.currentTimeMillis());
            result.setLongitude(first.getLongitude());
            result.setLatitude(first.getLatitude());
            return result;
        }
    }

    // ==============================================================
    // 车辆轨迹构建函数（按车牌Keyed，收集该车一段时间内的所有过车记录）
    // ==============================================================
    public static class VehicleTrackBuildFunction
            extends KeyedProcessFunction<String, TrafficCaptureData, Tuple2<String, List<TrafficCaptureData>>> {

        private transient ListState<TrafficCaptureData> trackState;
        private transient ValueState<Long> lastUpdateState;

        @Override
        public void open(Configuration parameters) {
            ListStateDescriptor<TrafficCaptureData> trackDesc =
                    new ListStateDescriptor<>("vehicleTrack", TrafficCaptureData.class);
            trackState = getRuntimeContext().getListState(trackDesc);

            ValueStateDescriptor<Long> lastUpdateDesc =
                    new ValueStateDescriptor<>("lastUpdate", Long.class);
            lastUpdateState = getRuntimeContext().getState(lastUpdateDesc);
        }

        @Override
        public void processElement(TrafficCaptureData value, Context ctx,
                                   Collector<Tuple2<String, List<TrafficCaptureData>>> out) throws Exception {
            long now = ctx.timestamp();
            long cutoff = now - TRACK_TTL_SECONDS * 1000;

            List<TrafficCaptureData> track = new ArrayList<>();
            Iterator<TrafficCaptureData> it = trackState.get().iterator();
            while (it.hasNext()) {
                TrafficCaptureData data = it.next();
                if (data.getEventTimestamp() < cutoff) {
                    continue;
                }
                track.add(data);
            }

            track.add(value);
            track.sort(Comparator.comparing(TrafficCaptureData::getCaptureTime));

            trackState.update(track);
            lastUpdateState.update(now);

            if (track.size() >= MIN_ROUTE_SEGMENT_COUNT) {
                out.collect(Tuple2.of(value.getPlateNo(), new ArrayList<>(track)));
            }
        }
    }

    // ==============================================================
    // 模型2：同路线多车匹配（按路线Keyed聚合，同一路线多车判定）
    // 思路：相同的卡口序列（A->B->C）若有>=THRESHOLD辆车在近似时间窗内经过，判定为跟车
    // ==============================================================
    public static class RouteMatchFunction
            extends KeyedProcessFunction<String, Tuple2<String, List<TrafficCaptureData>>, VehicleFollowResult> {

        private transient MapState<String, List<TrafficCaptureData>> routeVehicleMap;
        private transient ValueState<Long> lastCleanState;

        @Override
        public void open(Configuration parameters) {
            MapStateDescriptor<String, List<TrafficCaptureData>> mapDesc =
                    new MapStateDescriptor<>("routeVehicles",
                            String.class,
                            org.apache.flink.api.common.typeinfo.TypeInformation.of(
                                    new org.apache.flink.api.common.typeinfo.TypeHint<List<TrafficCaptureData>>() {}));
            routeVehicleMap = getRuntimeContext().getMapState(mapDesc);

            ValueStateDescriptor<Long> lastCleanDesc =
                    new ValueStateDescriptor<>("lastClean", Long.class);
            lastCleanState = getRuntimeContext().getState(lastCleanDesc);
        }

        @Override
        public void processElement(Tuple2<String, List<TrafficCaptureData>> value, Context ctx,
                                   Collector<VehicleFollowResult> out) throws Exception {
            String plateNo = value.f0;
            List<TrafficCaptureData> track = value.f1;

            long now = ctx.timestamp();
            Long lastClean = lastCleanState.value();
            if (lastClean == null || now - lastClean > 60 * 1000) {
                cleanupOldData(now);
                lastCleanState.update(now);
            }

            List<TrafficCaptureData> existing = routeVehicleMap.get(plateNo);
            if (existing == null || isNewerTrack(track, existing)) {
                routeVehicleMap.put(plateNo, track);
            }

            List<Map.Entry<String, List<TrafficCaptureData>>> allVehicles =
                    StreamSupport.stream(routeVehicleMap.entries().spliterator(), false)
                            .collect(Collectors.toList());

            List<Map.Entry<String, List<TrafficCaptureData>>> sameRouteVehicles = new ArrayList<>();
            for (Map.Entry<String, List<TrafficCaptureData>> entry : allVehicles) {
                if (routesSimilar(track, entry.getValue())) {
                    sameRouteVehicles.add(entry);
                }
            }

            if (sameRouteVehicles.size() >= FOLLOW_VEHICLE_THRESHOLD) {
                VehicleFollowResult result = buildRouteFollowResult(sameRouteVehicles);
                if (result != null) {
                    out.collect(result);
                    log.debug("【轨迹序列模型】命中跟车：routeSize={}, vehicles={}",
                            sameRouteVehicles.size(),
                            sameRouteVehicles.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
                }
            }
        }

        private void cleanupOldData(long now) throws Exception {
            long cutoff = now - TRACK_TTL_SECONDS * 1000;
            Iterator<Map.Entry<String, List<TrafficCaptureData>>> it = routeVehicleMap.entries().iterator();
            while (it.hasNext()) {
                Map.Entry<String, List<TrafficCaptureData>> entry = it.next();
                List<TrafficCaptureData> data = entry.getValue();
                if (data == null || data.isEmpty()) {
                    it.remove();
                    continue;
                }
                TrafficCaptureData last = data.get(data.size() - 1);
                if (last.getEventTimestamp() < cutoff) {
                    it.remove();
                }
            }
        }

        private boolean isNewerTrack(List<TrafficCaptureData> a, List<TrafficCaptureData> b) {
            if (a == null || a.isEmpty()) return false;
            if (b == null || b.isEmpty()) return true;
            return a.get(a.size() - 1).getEventTimestamp() > b.get(b.size() - 1).getEventTimestamp();
        }

        private boolean routesSimilar(List<TrafficCaptureData> a, List<TrafficCaptureData> b) {
            if (a == null || b == null || a.size() < MIN_ROUTE_SEGMENT_COUNT || b.size() < MIN_ROUTE_SEGMENT_COUNT) {
                return false;
            }
            List<String> routeA = a.stream().map(TrafficCaptureData::getCrossingId).collect(Collectors.toList());
            List<String> routeB = b.stream().map(TrafficCaptureData::getCrossingId).collect(Collectors.toList());

            int minOverlap = Math.min(routeA.size(), routeB.size()) / 2 + 1;
            int commonCount = 0;
            Set<String> setA = new HashSet<>(routeA);
            for (String id : routeB) {
                if (setA.contains(id)) commonCount++;
            }
            if (commonCount < Math.max(minOverlap, MIN_ROUTE_SEGMENT_COUNT)) {
                return false;
            }

            int orderMatch = 0;
            int idxA = 0, idxB = 0;
            while (idxA < routeA.size() && idxB < routeB.size()) {
                if (routeA.get(idxA).equals(routeB.get(idxB))) {
                    orderMatch++;
                    idxA++;
                    idxB++;
                } else if (idxA + 1 < routeA.size() && routeA.get(idxA + 1).equals(routeB.get(idxB))) {
                    idxA++;
                } else if (idxB + 1 < routeB.size() && routeA.get(idxA).equals(routeB.get(idxB + 1))) {
                    idxB++;
                } else {
                    idxA++;
                }
            }
            return orderMatch >= Math.max(minOverlap, MIN_ROUTE_SEGMENT_COUNT);
        }

        private VehicleFollowResult buildRouteFollowResult(
                List<Map.Entry<String, List<TrafficCaptureData>>> vehicles) {
            try {
                List<String> plateNos = vehicles.stream()
                        .map(Map.Entry::getKey)
                        .distinct()
                        .collect(Collectors.toList());

                if (plateNos.size() < FOLLOW_VEHICLE_THRESHOLD) return null;

                List<TrafficCaptureData> allPoints = new ArrayList<>();
                Set<String> crossingIds = new LinkedHashSet<>();
                Set<String> crossingNames = new LinkedHashSet<>();
                Set<String> vehicleTypes = new HashSet<>();
                BigDecimal firstLongitude = null;
                BigDecimal firstLatitude = null;
                LocalDateTime minTime = null;
                LocalDateTime maxTime = null;
                BigDecimal totalSpeed = BigDecimal.ZERO;
                int speedCount = 0;

                for (Map.Entry<String, List<TrafficCaptureData>> entry : vehicles) {
                    for (TrafficCaptureData data : entry.getValue()) {
                        allPoints.add(data);
                        crossingIds.add(data.getCrossingId());
                        if (data.getCrossingName() != null) crossingNames.add(data.getCrossingName());
                        if (data.getVehicleType() != null) vehicleTypes.add(data.getVehicleType());
                        if (firstLongitude == null && data.getLongitude() != null) {
                            firstLongitude = data.getLongitude();
                            firstLatitude = data.getLatitude();
                        }
                        if (data.getCaptureTime() != null) {
                            if (minTime == null || data.getCaptureTime().isBefore(minTime)) {
                                minTime = data.getCaptureTime();
                            }
                            if (maxTime == null || data.getCaptureTime().isAfter(maxTime)) {
                                maxTime = data.getCaptureTime();
                            }
                        }
                        if (data.getSpeed() != null) {
                            totalSpeed = totalSpeed.add(data.getSpeed());
                            speedCount++;
                        }
                    }
                }

                VehicleFollowResult result = new VehicleFollowResult();
                result.setAlertId("VF-ROUTE-" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
                result.setAlertNo("VF-ROUTE-" + System.currentTimeMillis());
                result.setCrossingId(String.join("->", crossingIds));
                result.setCrossingName(String.join("→", crossingNames));
                result.setRoadDirection("轨迹序列");
                result.setVehicleCount(plateNos.size());
                result.setPlateNos(plateNos);
                result.setVehicleTypes(new ArrayList<>(vehicleTypes));

                if (speedCount > 0) {
                    result.setAverageSpeed(totalSpeed.divide(BigDecimal.valueOf(speedCount), 2, RoundingMode.HALF_UP));
                }

                result.setWindowStart(minTime);
                result.setWindowEnd(maxTime);

                int level;
                if (plateNos.size() >= 10) level = 1;
                else if (plateNos.size() >= 5) level = 2;
                else level = 3;
                result.setAlertLevel(level);

                result.setDescription(String.format("跟车告警[轨迹序列模型]：检测到%d辆车沿相同路线行驶（经过%d个卡口：%s），时间区间[%s ~ %s]",
                        plateNos.size(),
                        crossingIds.size(),
                        String.join("→", crossingNames),
                        minTime, maxTime));
                result.setTimestamp(System.currentTimeMillis());
                result.setLongitude(firstLongitude);
                result.setLatitude(firstLatitude);
                return result;
            } catch (Exception e) {
                log.error("构建路线跟车结果失败：{}", e.getMessage());
                return null;
            }
        }
    }

    // ==============================================================
    // 冷却去重函数
    // ==============================================================
    public static class FollowCooldownFunction
            extends KeyedProcessFunction<String, VehicleFollowResult, VehicleFollowResult> {

        private transient MapState<String, Long> lastAlertTimeMap;

        @Override
        public void open(Configuration parameters) {
            lastAlertTimeMap = getRuntimeContext().getMapState(
                    new MapStateDescriptor<>("followCooldown", String.class, Long.class));
        }

        @Override
        public void processElement(VehicleFollowResult value, Context ctx,
                                   Collector<VehicleFollowResult> out) throws Exception {
            String dedupKey = value.getCrossingId() + "_" +
                    (value.getPlateNos() != null ? String.join(",", value.getPlateNos()) : "");
            Long lastAlert = lastAlertTimeMap.get(dedupKey);
            long now = System.currentTimeMillis();
            long cooldownMs = COOLDOWN_MINUTES * 60 * 1000L;
            if (lastAlert == null || (now - lastAlert) > cooldownMs) {
                lastAlertTimeMap.put(dedupKey, now);
                String globalKey = dedupKey + ":" + now / 1000;
                ALERT_COOLDOWN.add(globalKey);
                out.collect(value);
                log.warn("【跟车告警】输出：crossing={}, vehicles={}, level={}",
                        value.getCrossingName(), value.getVehicleCount(), value.getAlertLevel());
            } else {
                log.debug("【跟车告警】冷却期跳过：key={}, last={}s前", dedupKey, (now - lastAlert) / 1000);
            }
        }
    }

    // ==============================================================
    // Sink: RocketMQ
    // ==============================================================
    public static class FollowAlertRocketMQSink
            extends org.apache.flink.streaming.api.functions.sink.RichSinkFunction<VehicleFollowResult> {

        private final FlinkConfig flinkConfig;
        private transient org.apache.rocketmq.client.producer.DefaultMQProducer producer;

        public FollowAlertRocketMQSink(FlinkConfig config) { this.flinkConfig = config; }

        @Override
        public void open(Configuration parameters) {
            try {
                producer = new org.apache.rocketmq.client.producer.DefaultMQProducer("flink-follow-sink-group");
                producer.setNamesrvAddr(flinkConfig.getNameServer());
                producer.start();
            } catch (Exception e) {
                log.error("RocketMQ Producer启动失败：{}", e.getMessage());
            }
        }

        @Override
        public void invoke(VehicleFollowResult value, Context context) {
            try {
                if (producer == null) return;
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "vehicle_follow");
                msg.put("data", value);
                msg.put("timestamp", System.currentTimeMillis());
                String json = JSON.toJSONString(msg);

                org.apache.rocketmq.common.message.Message mqMsg = new org.apache.rocketmq.common.message.Message(
                        flinkConfig.getKafka().getVehicleControlAlertTopic(),
                        "vehicle_follow",
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                producer.sendOneway(mqMsg);

                org.apache.rocketmq.common.message.Message wsMsg = new org.apache.rocketmq.common.message.Message(
                        flinkConfig.getWebsocketPushTopic(),
                        "vehicle_follow",
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                producer.sendOneway(wsMsg);
            } catch (Exception e) {
                log.error("跟车告警发送MQ失败：{}", e.getMessage());
            }
        }

        @Override
        public void close() {
            if (producer != null) producer.shutdown();
        }
    }

    public static class FollowAlertWebSocketSink
            extends org.apache.flink.streaming.api.functions.sink.RichSinkFunction<VehicleFollowResult> {
        @Override
        public void invoke(VehicleFollowResult value, Context context) {
            log.info("跟车告警WebSocket推送：crossing={}, count={}, model=V2",
                    value.getCrossingName(), value.getVehicleCount());
        }
    }
}
