package com.police.vision.flink.job;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.entity.TrafficCaptureData;
import com.police.vision.flink.config.FlinkConfig;
import com.police.vision.flink.entity.NightActiveVehicleResult;
import com.police.vision.flink.schema.TrafficCaptureSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NightActiveVehicleJob implements CommandLineRunner {

    private final FlinkConfig flinkConfig;

    public static final int NIGHT_CAPTURE_THRESHOLD = 5;
    public static final double NIGHT_DAY_RATIO_THRESHOLD = 2.0;
    public static final int STATISTICS_DAYS = 3;
    public static final int COOLDOWN_HOURS = 24;

    public static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    public static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    public void runJob() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        KafkaSource<TrafficCaptureData> kafkaSource = KafkaSource.<TrafficCaptureData>builder()
                .setBootstrapServers(flinkConfig.getKafka().getBootstrapServers())
                .setTopics(flinkConfig.getKafka().getTrafficCaptureTopic())
                .setGroupId("flink-night-active-consumer-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TrafficCaptureSchema())
                .build();

        DataStream<TrafficCaptureData> events = env
                .fromSource(kafkaSource, WatermarkStrategy
                                .<TrafficCaptureData>forBoundedOutOfOrderness(Duration.ofSeconds(30))
                                .withTimestampAssigner((event, ts) -> event.getEventTimestamp()),
                        "TrafficCaptureSource"
                )
                .name("TrafficCaptureKafkaSource-Night")
                .filter(e -> e.getPlateNo() != null && !e.getPlateNo().isEmpty())
                .name("FilterValidEvents");

        KeyedStream<TrafficCaptureData, String> plateKeyed = events
                .keyBy(TrafficCaptureData::getPlateNo);

        SingleOutputStreamOperator<NightActiveVehicleResult> alertStream = plateKeyed
                .process(new NightActiveAnalysisFunction())
                .name("NightActiveAnalysis");

        alertStream.addSink(new NightActiveAlertRocketMQSink(flinkConfig))
                .name("PushToRocketMQ");

        alertStream.addSink(new NightActiveAlertWebSocketSink())
                .name("PushToWebSocket");

        env.executeAsync("NightActiveVehicle-Job");
        log.info("Flink昼伏夜出分析任务已启动：夜间阈值={}次，日夜比={}",
                NIGHT_CAPTURE_THRESHOLD, NIGHT_DAY_RATIO_THRESHOLD);
    }

    @Override
    public void run(String... args) {
        try {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(20000L);
                    runJob();
                } catch (Exception e) {
                    log.error("启动Flink昼伏夜出分析任务失败：{}", e.getMessage(), e);
                }
            });
            thread.setDaemon(true);
            thread.setName("Flink-Night-Active-Vehicle");
            thread.start();
        } catch (Exception e) {
            log.warn("Flink昼伏夜出任务启动线程创建失败：{}", e.getMessage());
        }
    }

    public static class NightActiveAnalysisFunction
            extends KeyedProcessFunction<String, TrafficCaptureData, NightActiveVehicleResult> {

        private transient ListState<TrafficCaptureData> captureDataList;
        private transient ValueState<Long> lastAlertTime;

        @Override
        public void open(Configuration parameters) {
            ListStateDescriptor<TrafficCaptureData> listDescriptor =
                    new ListStateDescriptor<>("captureDataList", TrafficCaptureData.class);
            captureDataList = getRuntimeContext().getListState(listDescriptor);

            ValueStateDescriptor<Long> lastAlertDescriptor =
                    new ValueStateDescriptor<>("lastAlertTime", Long.class);
            lastAlertTime = getRuntimeContext().getState(lastAlertDescriptor);
        }

        @Override
        public void processElement(TrafficCaptureData value, Context ctx,
                                   Collector<NightActiveVehicleResult> out) throws Exception {
            captureDataList.add(value);

            long now = System.currentTimeMillis();
            long cutoffTime = now - STATISTICS_DAYS * 24 * 60 * 60 * 1000L;

            List<TrafficCaptureData> recentData = new ArrayList<>();
            int nightCount = 0;
            int dayCount = 0;
            Set<String> crossingSet = new HashSet<>();
            Set<String> crossingNameSet = new HashSet<>();
            TrafficCaptureData latestData = null;

            Iterator<TrafficCaptureData> iterator = captureDataList.get().iterator();
            while (iterator.hasNext()) {
                TrafficCaptureData data = iterator.next();
                long eventTime = data.getEventTimestamp();
                if (eventTime < cutoffTime) {
                    iterator.remove();
                    continue;
                }
                recentData.add(data);

                if (latestData == null || data.getCaptureTime().isAfter(latestData.getCaptureTime())) {
                    latestData = data;
                }

                if (data.getCrossingId() != null) {
                    crossingSet.add(data.getCrossingId());
                }
                if (data.getCrossingName() != null) {
                    crossingNameSet.add(data.getCrossingName());
                }

                if (data.getCaptureTime() != null) {
                    LocalTime time = data.getCaptureTime().toLocalTime();
                    if (isNightTime(time)) {
                        nightCount++;
                    } else {
                        dayCount++;
                    }
                }
            }

            if (nightCount >= NIGHT_CAPTURE_THRESHOLD && dayCount > 0) {
                double ratio = (double) nightCount / dayCount;
                if (ratio >= NIGHT_DAY_RATIO_THRESHOLD) {
                    Long lastAlert = lastAlertTime.value();
                    long cooldownMs = COOLDOWN_HOURS * 60 * 60 * 1000L;
                    if (lastAlert == null || (now - lastAlert) > cooldownMs) {
                        lastAlertTime.update(now);

                        NightActiveVehicleResult result = new NightActiveVehicleResult();
                        result.setAlertId("NA" + System.currentTimeMillis() +
                                UUID.randomUUID().toString().replace("-", "").substring(0, 6));
                        result.setAlertNo("NA" + System.currentTimeMillis());
                        result.setPlateNo(value.getPlateNo());
                        result.setVehicleType(value.getVehicleType());
                        result.setVehicleColor(value.getVehicleColor());
                        result.setNightCaptureCount(nightCount);
                        result.setDayCaptureCount(dayCount);
                        result.setNightDayRatio(BigDecimal.valueOf(ratio)
                                .setScale(2, RoundingMode.HALF_UP));
                        result.setCrossingIds(new ArrayList<>(crossingSet));
                        result.setCrossingNames(new ArrayList<>(crossingNameSet));

                        if (!recentData.isEmpty()) {
                            LocalDateTime firstTime = recentData.stream()
                                    .map(TrafficCaptureData::getCaptureTime)
                                    .filter(Objects::nonNull)
                                    .min(LocalDateTime::compareTo)
                                    .orElse(null);
                            LocalDateTime lastTime = recentData.stream()
                                    .map(TrafficCaptureData::getCaptureTime)
                                    .filter(Objects::nonNull)
                                    .max(LocalDateTime::compareTo)
                                    .orElse(null);
                            result.setStatisticsStartTime(firstTime);
                            result.setStatisticsEndTime(lastTime);
                        }

                        int level;
                        if (nightCount >= 20) level = 1;
                        else if (nightCount >= 10) level = 2;
                        else level = 3;
                        result.setAlertLevel(level);

                        result.setDescription(String.format("昼伏夜出车辆告警：车牌[%s]，近%d天夜间出现%d次，白天出现%d次，日夜比%.2f",
                                value.getPlateNo(), STATISTICS_DAYS, nightCount, dayCount, ratio));

                        result.setTimestamp(System.currentTimeMillis());
                        if (latestData != null) {
                            result.setLastLongitude(latestData.getLongitude());
                            result.setLastLatitude(latestData.getLatitude());
                            result.setLastCaptureTime(latestData.getCaptureTime());
                        }

                        out.collect(result);
                        log.warn("【昼伏夜出告警】输出告警：plateNo={}, nightCount={}, dayCount={}, ratio={:.2f}",
                                value.getPlateNo(), nightCount, dayCount, ratio);
                    }
                }
            }
        }

        private boolean isNightTime(LocalTime time) {
            if (NIGHT_END.isBefore(NIGHT_START)) {
                return time.isAfter(NIGHT_START) || time.isBefore(NIGHT_END);
            } else {
                return time.isAfter(NIGHT_START) && time.isBefore(NIGHT_END);
            }
        }
    }

    public static class NightActiveAlertRocketMQSink
            extends org.apache.flink.streaming.api.functions.sink.RichSinkFunction<NightActiveVehicleResult> {

        private final FlinkConfig flinkConfig;
        private transient org.apache.rocketmq.client.producer.DefaultMQProducer producer;

        public NightActiveAlertRocketMQSink(FlinkConfig config) { this.flinkConfig = config; }

        @Override
        public void open(Configuration parameters) {
            try {
                producer = new org.apache.rocketmq.client.producer.DefaultMQProducer("flink-night-sink-group");
                producer.setNamesrvAddr(flinkConfig.getNameServer());
                producer.start();
            } catch (Exception e) {
                log.error("RocketMQ Producer启动失败：{}", e.getMessage());
            }
        }

        @Override
        public void invoke(NightActiveVehicleResult value, Context context) {
            try {
                if (producer == null) return;
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "vehicle_night_active");
                msg.put("data", value);
                msg.put("timestamp", System.currentTimeMillis());
                String json = JSON.toJSONString(msg);

                org.apache.rocketmq.common.message.Message mqMsg = new org.apache.rocketmq.common.message.Message(
                        flinkConfig.getKafka().getVehicleControlAlertTopic(),
                        "vehicle_night_active",
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                producer.sendOneway(mqMsg);

                org.apache.rocketmq.common.message.Message wsMsg = new org.apache.rocketmq.common.message.Message(
                        flinkConfig.getWebsocketPushTopic(),
                        "vehicle_night_active",
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                producer.sendOneway(wsMsg);
            } catch (Exception e) {
                log.error("昼伏夜出告警发送MQ失败：{}", e.getMessage());
            }
        }

        @Override
        public void close() {
            if (producer != null) producer.shutdown();
        }
    }

    public static class NightActiveAlertWebSocketSink
            extends org.apache.flink.streaming.api.functions.sink.RichSinkFunction<NightActiveVehicleResult> {
        @Override
        public void invoke(NightActiveVehicleResult value, Context context) {
            log.info("昼伏夜出告警WebSocket推送：plateNo={}, nightCount={}",
                    value.getPlateNo(), value.getNightCaptureCount());
        }
    }
}
