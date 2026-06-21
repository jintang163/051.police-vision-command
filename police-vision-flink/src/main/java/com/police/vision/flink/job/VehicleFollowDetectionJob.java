package com.police.vision.flink.job;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.entity.TrafficCaptureData;
import com.police.vision.flink.config.FlinkConfig;
import com.police.vision.flink.entity.VehicleFollowResult;
import com.police.vision.flink.schema.TrafficCaptureSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleFollowDetectionJob implements CommandLineRunner {

    private final FlinkConfig flinkConfig;

    public static final int FOLLOW_VEHICLE_THRESHOLD = 3;
    public static final int WINDOW_MINUTES = 5;
    public static final int SLIDE_MINUTES = 1;
    public static final int COOLDOWN_MINUTES = 30;
    public static final int MAX_TIME_DIFF_SECONDS = 120;

    private static final Set<String> ALERT_COOLDOWN = ConcurrentHashMap.newKeySet();

    public void runJob() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        KafkaSource<TrafficCaptureData> kafkaSource = KafkaSource.<TrafficCaptureData>builder()
                .setBootstrapServers(flinkConfig.getKafka().getBootstrapServers())
                .setTopics(flinkConfig.getKafka().getTrafficCaptureTopic())
                .setGroupId(flinkConfig.getKafka().getConsumerGroup())
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TrafficCaptureSchema())
                .build();

        DataStream<TrafficCaptureData> events = env
                .fromSource(kafkaSource, WatermarkStrategy
                                .<TrafficCaptureData>forBoundedOutOfOrderness(Duration.ofSeconds(30))
                                .withTimestampAssigner((event, ts) -> event.getEventTimestamp()),
                        "TrafficCaptureSource"
                )
                .name("TrafficCaptureKafkaSource")
                .filter(e -> e.getPlateNo() != null && !e.getPlateNo().isEmpty())
                .name("FilterValidEvents");

        KeyedStream<TrafficCaptureData, String> crossingKeyed = events
                .keyBy(event -> event.getCrossingId() + "_" + event.getDirection());

        SingleOutputStreamOperator<VehicleFollowResult> windowedFollowStream = crossingKeyed
                .window(SlidingEventTimeWindows.of(
                        Time.minutes(WINDOW_MINUTES), Time.minutes(SLIDE_MINUTES)))
                .aggregate(new VehicleFollowAggregateFunction())
                .filter(result -> result.getVehicleCount() >= FOLLOW_VEHICLE_THRESHOLD)
                .name("WindowedFollowDetection");

        DataStream<VehicleFollowResult> alertStream = windowedFollowStream
                .keyBy(VehicleFollowResult::getCrossingId)
                .process(new FollowCooldownFunction())
                .name("CooldownAndDedup");

        alertStream.addSink(new FollowAlertRocketMQSink(flinkConfig))
                .name("PushToRocketMQ");

        alertStream.addSink(new FollowAlertWebSocketSink())
                .name("PushToWebSocket");

        env.executeAsync("VehicleFollowDetection-Job");
        log.info("Flink跟车分析任务已启动：阈值={}辆，窗口={}分钟，滑动={}分钟",
                FOLLOW_VEHICLE_THRESHOLD, WINDOW_MINUTES, SLIDE_MINUTES);
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

    public static class VehicleFollowAggregateFunction implements
            AggregateFunction<TrafficCaptureData, VehicleFollowAccumulator, VehicleFollowResult> {

        @Override
        public VehicleFollowAccumulator createAccumulator() {
            return new VehicleFollowAccumulator();
        }

        @Override
        public VehicleFollowAccumulator add(TrafficCaptureData value, VehicleFollowAccumulator acc) {
            if (!acc.plateSet.contains(value.getPlateNo())) {
                acc.plateSet.add(value.getPlateNo());
                acc.vehicleList.add(value);
                if (value.getVehicleType() != null && !acc.vehicleTypeSet.contains(value.getVehicleType())) {
                    acc.vehicleTypeSet.add(value.getVehicleType());
                }
                if (value.getSpeed() != null) {
                    acc.speedSum = acc.speedSum.add(value.getSpeed());
                    acc.speedCount++;
                }
                if (value.getCaptureTime() != null) {
                    if (acc.startTime == null || value.getCaptureTime().isBefore(acc.startTime)) {
                        acc.startTime = value.getCaptureTime();
                    }
                    if (acc.endTime == null || value.getCaptureTime().isAfter(acc.endTime)) {
                        acc.endTime = value.getCaptureTime();
                    }
                }
            }
            acc.crossingId = value.getCrossingId();
            acc.crossingName = value.getCrossingName();
            acc.direction = value.getDirection();
            if (value.getLongitude() != null && value.getLatitude() != null) {
                acc.longitude = value.getLongitude();
                acc.latitude = value.getLatitude();
            }
            acc.totalCaptureCount++;
            return acc;
        }

        @Override
        public VehicleFollowResult getResult(VehicleFollowAccumulator acc) {
            VehicleFollowResult result = new VehicleFollowResult();
            result.setAlertId("VF" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
            result.setAlertNo("VF" + System.currentTimeMillis());
            result.setCrossingId(acc.crossingId);
            result.setCrossingName(acc.crossingName);
            result.setRoadDirection(acc.direction != null ? String.valueOf(acc.direction) : "");
            result.setVehicleCount(acc.plateSet.size());
            result.setPlateNos(new ArrayList<>(acc.plateSet));
            result.setVehicleTypes(new ArrayList<>(acc.vehicleTypeSet));
            result.setLongitude(acc.longitude);
            result.setLatitude(acc.latitude);

            if (acc.speedCount > 0) {
                result.setAverageSpeed(acc.speedSum.divide(BigDecimal.valueOf(acc.speedCount), 2, BigDecimal.ROUND_HALF_UP));
            }

            result.setWindowStart(acc.startTime);
            result.setWindowEnd(acc.endTime);

            int count = acc.plateSet.size();
            int level;
            if (count >= 10) level = 1;
            else if (count >= 5) level = 2;
            else level = 3;
            result.setAlertLevel(level);

            String desc = String.format("跟车分析告警：卡口[%s]检测到%d辆车同行，时间窗口%d分钟",
                    acc.crossingName, acc.plateSet.size(), WINDOW_MINUTES);
            result.setDescription(desc);
            result.setTimestamp(System.currentTimeMillis());

            return result;
        }

        @Override
        public VehicleFollowAccumulator merge(VehicleFollowAccumulator a, VehicleFollowAccumulator b) {
            VehicleFollowAccumulator m = new VehicleFollowAccumulator();
            m.plateSet.addAll(a.plateSet); m.plateSet.addAll(b.plateSet);
            m.vehicleTypeSet.addAll(a.vehicleTypeSet); m.vehicleTypeSet.addAll(b.vehicleTypeSet);
            m.vehicleList.addAll(a.vehicleList); m.vehicleList.addAll(b.vehicleList);
            m.speedSum = a.speedSum.add(b.speedSum);
            m.speedCount = a.speedCount + b.speedCount;
            m.totalCaptureCount = a.totalCaptureCount + b.totalCaptureCount;
            m.crossingId = a.crossingId != null ? a.crossingId : b.crossingId;
            m.crossingName = a.crossingName != null ? a.crossingName : b.crossingName;
            m.direction = a.direction != null ? a.direction : b.direction;
            m.longitude = a.longitude != null ? a.longitude : b.longitude;
            m.latitude = a.latitude != null ? a.latitude : b.latitude;
            if (a.startTime != null && b.startTime != null) {
                m.startTime = a.startTime.isBefore(b.startTime) ? a.startTime : b.startTime;
            } else if (a.startTime != null) { m.startTime = a.startTime; } else { m.startTime = b.startTime; }
            if (a.endTime != null && b.endTime != null) {
                m.endTime = a.endTime.isAfter(b.endTime) ? a.endTime : b.endTime;
            } else if (a.endTime != null) { m.endTime = a.endTime; } else { m.endTime = b.endTime; }
            return m;
        }
    }

    public static class VehicleFollowAccumulator implements java.io.Serializable {
        Set<String> plateSet = new HashSet<>();
        Set<String> vehicleTypeSet = new HashSet<>();
        List<TrafficCaptureData> vehicleList = new ArrayList<>();
        BigDecimal speedSum = BigDecimal.ZERO;
        int speedCount = 0;
        int totalCaptureCount = 0;
        String crossingId;
        String crossingName;
        Integer direction;
        BigDecimal longitude;
        BigDecimal latitude;
        LocalDateTime startTime;
        LocalDateTime endTime;
    }

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
            String dedupKey = value.getCrossingId() + "_" + value.getVehicleCount();
            Long lastAlert = lastAlertTimeMap.get(dedupKey);
            long now = System.currentTimeMillis();
            long cooldownMs = COOLDOWN_MINUTES * 60 * 1000L;
            if (lastAlert == null || (now - lastAlert) > cooldownMs) {
                lastAlertTimeMap.put(dedupKey, now);
                String globalKey = value.getCrossingId() + ":" + now / 1000;
                ALERT_COOLDOWN.add(globalKey);
                out.collect(value);
                log.warn("【跟车告警】输出告警：crossing={}, vehicles={}, level={}",
                        value.getCrossingName(), value.getVehicleCount(), value.getAlertLevel());
            } else {
                log.debug("【跟车告警】冷却期跳过：crossing={}, lastAlert={}s前",
                        value.getCrossingName(), (now - lastAlert) / 1000);
            }
        }
    }

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
            log.info("跟车告警WebSocket推送：crossing={}, count={}",
                    value.getCrossingName(), value.getVehicleCount());
        }
    }
}
