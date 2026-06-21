package com.police.vision.flink.job;

import com.alibaba.fastjson2.JSON;
import com.police.vision.flink.config.FlinkConfig;
import com.police.vision.flink.entity.AggregationAlertResult;
import com.police.vision.flink.entity.FaceCaptureEvent;
import com.police.vision.flink.schema.FaceCaptureSchema;
import com.police.vision.flink.sink.WebSocketPushSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.rocketmq.source.RocketMQSource;
import org.apache.flink.connector.rocketmq.source.RocketMQSourceOptions;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.rocketmq.flink.common.serialization.SimpleKeyValueSerializationSchema;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationDetectionJob implements CommandLineRunner {

    private final FlinkConfig flinkConfig;

    public static final int AGGREGATION_THRESHOLD = 3;
    public static final int WINDOW_MINUTES = 10;
    public static final int SLIDE_MINUTES = 2;
    public static final int COOLDOWN_MINUTES = 30;

    private static final Set<String> ALERT_COOLDOWN = ConcurrentHashMap.newKeySet();

    public void runJob() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        RocketMQSource<FaceCaptureEvent> source = RocketMQSource.<FaceCaptureEvent>builder()
                .setTopicName(flinkConfig.getFaceCaptureTopic())
                .setConsumerGroup("flink-aggregation-detection-group")
                .setNameServerAddress(flinkConfig.getNameServer())
                .setStartMessageOffset(RocketMQSourceOptions.START_LATEST)
                .setDeserializationSchema(new FaceCaptureSchema())
                .build();

        DataStream<FaceCaptureEvent> events = env
                .fromSource(source, WatermarkStrategy
                        .<FaceCaptureEvent>forBoundedOutOfOrderness(Duration.ofSeconds(30))
                        .withTimestampAssigner((event, ts) -> event.getEventTimestamp()),
                        "FaceCaptureEvents"
                )
                .name("FaceCaptureSource")
                .filter(e -> e.getPersonId() != null && !"UNKNOWN".equals(e.getPersonId()))
                .name("FilterValidEvents");

        KeyedStream<FaceCaptureEvent, String> areaKeyed = events
                .keyBy(FaceCaptureEvent::getAreaKey);

        SingleOutputStreamOperator<AggregationAlertResult> windowedAlertStream = areaKeyed
                .window(SlidingEventTimeWindows.of(
                        Time.minutes(WINDOW_MINUTES), Time.minutes(SLIDE_MINUTES)))
                .aggregate(new AreaAggregationFunction())
                .filter(result -> result.getTotalPersonCount() >= AGGREGATION_THRESHOLD)
                .name("WindowedAggregation");

        Pattern<FaceCaptureEvent, ?> multiPersonPattern = Pattern
                .<FaceCaptureEvent>begin("firstCapture")
                .where(new SimpleCondition<FaceCaptureEvent>() {
                    @Override
                    public boolean filter(FaceCaptureEvent value) {
                        return value.getPersonId() != null;
                    }
                })
                .followedByAny("secondCapture")
                .where(new SimpleCondition<FaceCaptureEvent>() {
                    @Override
                    public boolean filter(FaceCaptureEvent value) {
                        return value.getPersonId() != null;
                    }
                })
                .followedByAny("thirdCapture")
                .where(new SimpleCondition<FaceCaptureEvent>() {
                    @Override
                    public boolean filter(FaceCaptureEvent value) {
                        return value.getPersonId() != null;
                    }
                })
                .within(Time.minutes(WINDOW_MINUTES));

        PatternStream<FaceCaptureEvent> patternStream = CEP.pattern(areaKeyed, multiPersonPattern);

        SingleOutputStreamOperator<AggregationAlertResult> cepAlertStream = patternStream
                .process(new AggregationPatternProcessFunction())
                .name("CEP_Pattern_Match");

        DataStream<AggregationAlertResult> mergedAlerts = windowedAlertStream
                .union(cepAlertStream)
                .keyBy(AggregationAlertResult::getAreaCode)
                .process(new AggregationCooldownFunction())
                .name("CooldownAndDedup");

        mergedAlerts.addSink(new WebSocketPushSink<>())
                .name("PushToWebSocket");

        mergedAlerts.addSink(new AggregationAlertRocketMQSink(flinkConfig))
                .name("PushToRocketMQ");

        env.executeAsync("AggregationDetection-CEP-Job");
        log.info("Flink CEP异常聚集检测任务已启动：阈值={}人，窗口={}分钟",
                AGGREGATION_THRESHOLD, WINDOW_MINUTES);
    }

    @Override
    public void run(String... args) {
        try {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(10000L);
                    runJob();
                } catch (Exception e) {
                    log.error("启动Flink CEP异常聚集检测任务失败：{}", e.getMessage(), e);
                }
            });
            thread.setDaemon(true);
            thread.setName("Flink-Aggregation-Detection");
            thread.start();
        } catch (Exception e) {
            log.warn("Flink CEP任务启动线程创建失败：{}", e.getMessage());
        }
    }

    public static class AreaAggregationFunction implements
            AggregateFunction<FaceCaptureEvent, AreaAccumulator, AggregationAlertResult> {

        @Override
        public AreaAccumulator createAccumulator() {
            return new AreaAccumulator();
        }

        @Override
        public AreaAccumulator add(FaceCaptureEvent value, AreaAccumulator acc) {
            if (!acc.personSet.contains(value.getPersonId())) {
                acc.personSet.add(value.getPersonId());
                if (value.getPersonName() != null) {
                    acc.personNameMap.put(value.getPersonId(), value.getPersonName());
                }
            }
            if (Boolean.TRUE.equals(value.getIsTargetPerson())) {
                if (!acc.targetPersonSet.contains(value.getPersonId())) {
                    acc.targetPersonSet.add(value.getPersonId());
                    if (value.getPersonName() != null) {
                        acc.targetPersonNameMap.put(value.getPersonId(), value.getPersonName());
                    }
                }
                acc.targetCaptureCount++;
            }
            if (value.getCameraId() != null) {
                acc.cameraSet.add(value.getCameraId());
            }
            if (value.getLongitude() != null && value.getLatitude() != null) {
                acc.lngSum += value.getLongitudeDouble();
                acc.latSum += value.getLatitudeDouble();
                acc.coordCount++;
            }
            acc.totalCaptureCount++;
            if (acc.startTime == null || value.getEventTime().isBefore(acc.startTime)) {
                acc.startTime = value.getEventTime();
            }
            if (acc.endTime == null || value.getEventTime().isAfter(acc.endTime)) {
                acc.endTime = value.getEventTime();
            }
            acc.areaCode = value.getAreaKey();
            return acc;
        }

        @Override
        public AggregationAlertResult getResult(AreaAccumulator acc) {
            AggregationAlertResult r = new AggregationAlertResult();
            r.setAlertId("AG" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
            r.setAlertNo("AG" + System.currentTimeMillis());
            r.setAreaCode(acc.areaCode);
            r.setAreaName(acc.areaCode);
            r.setTotalPersonCount(acc.personSet.size());
            r.setTargetPersonCount(acc.targetPersonSet.size());
            r.setPersonIds(new ArrayList<>(acc.personSet));
            r.setPersonNames(new ArrayList<>(acc.personNameMap.values()));
            r.setTargetPersonIds(new ArrayList<>(acc.targetPersonSet));
            r.setTargetPersonNames(new ArrayList<>(acc.targetPersonNameMap.values()));
            r.setCameraIds(new ArrayList<>(acc.cameraSet));

            if (acc.coordCount > 0) {
                r.setCenterLongitude(BigDecimal.valueOf(acc.lngSum / acc.coordCount));
                r.setCenterLatitude(BigDecimal.valueOf(acc.latSum / acc.coordCount));
            }

            int targetCount = acc.targetPersonSet.size();
            int level;
            if (targetCount >= 5) level = 4;
            else if (targetCount >= 3) level = 3;
            else if (targetCount >= 1) level = 2;
            else level = Math.min(acc.personSet.size() / 3, 3) + 1;
            r.setAlertLevel(level);

            r.setStartTime(acc.startTime);
            r.setEndTime(acc.endTime);
            if (acc.startTime != null && acc.endTime != null) {
                long sec = java.time.Duration.between(acc.startTime, acc.endTime).getSeconds();
                r.setDurationSeconds((int) sec);
            } else {
                r.setDurationSeconds(0);
            }

            String desc = String.format("检测到异常聚集：区域[%s]，共%d人（含%d名重点人员），摄像头%d路，时间窗口%d分钟",
                    acc.areaCode, acc.personSet.size(), acc.targetPersonSet.size(),
                    acc.cameraSet.size(), WINDOW_MINUTES);
            r.setDescription(desc);
            r.setTimestamp(System.currentTimeMillis());
            return r;
        }

        @Override
        public AreaAccumulator merge(AreaAccumulator a, AreaAccumulator b) {
            AreaAccumulator m = new AreaAccumulator();
            m.personSet.addAll(a.personSet); m.personSet.addAll(b.personSet);
            m.targetPersonSet.addAll(a.targetPersonSet); m.targetPersonSet.addAll(b.targetPersonSet);
            m.cameraSet.addAll(a.cameraSet); m.cameraSet.addAll(b.cameraSet);
            m.personNameMap.putAll(a.personNameMap); m.personNameMap.putAll(b.personNameMap);
            m.targetPersonNameMap.putAll(a.targetPersonNameMap); m.targetPersonNameMap.putAll(b.targetPersonNameMap);
            m.lngSum = a.lngSum + b.lngSum;
            m.latSum = a.latSum + b.latSum;
            m.coordCount = a.coordCount + b.coordCount;
            m.totalCaptureCount = a.totalCaptureCount + b.totalCaptureCount;
            m.targetCaptureCount = a.targetCaptureCount + b.targetCaptureCount;
            m.areaCode = a.areaCode != null ? a.areaCode : b.areaCode;
            if (a.startTime != null && b.startTime != null) {
                m.startTime = a.startTime.isBefore(b.startTime) ? a.startTime : b.startTime;
            } else if (a.startTime != null) { m.startTime = a.startTime; } else { m.startTime = b.startTime; }
            if (a.endTime != null && b.endTime != null) {
                m.endTime = a.endTime.isAfter(b.endTime) ? a.endTime : b.endTime;
            } else if (a.endTime != null) { m.endTime = a.endTime; } else { m.endTime = b.endTime; }
            return m;
        }
    }

    public static class AreaAccumulator implements java.io.Serializable {
        Set<String> personSet = new HashSet<>();
        Set<String> targetPersonSet = new HashSet<>();
        Map<String, String> personNameMap = new HashMap<>();
        Map<String, String> targetPersonNameMap = new HashMap<>();
        Set<String> cameraSet = new HashSet<>();
        double lngSum = 0.0;
        double latSum = 0.0;
        int coordCount = 0;
        int totalCaptureCount = 0;
        int targetCaptureCount = 0;
        String areaCode;
        LocalDateTime startTime;
        LocalDateTime endTime;
    }

    public static class AggregationPatternProcessFunction
            extends PatternProcessFunction<FaceCaptureEvent, AggregationAlertResult> {
        @Override
        public void processMatch(Map<String, List<FaceCaptureEvent>> match, Context ctx,
                                 Collector<AggregationAlertResult> out) {
            try {
                List<FaceCaptureEvent> all = new ArrayList<>();
                for (List<FaceCaptureEvent> l : match.values()) all.addAll(l);
                if (all.isEmpty()) return;

                Set<String> personIds = new HashSet<>();
                Set<String> targetIds = new HashSet<>();
                Map<String, String> nameMap = new HashMap<>();
                Map<String, String> tgtNameMap = new HashMap<>();
                Set<String> cams = new HashSet<>();
                double lngSum = 0, latSum = 0;
                int coordCnt = 0;
                LocalDateTime start = null, end = null;
                String area = all.get(0).getAreaKey();

                for (FaceCaptureEvent e : all) {
                    personIds.add(e.getPersonId());
                    if (e.getPersonName() != null) nameMap.put(e.getPersonId(), e.getPersonName());
                    if (Boolean.TRUE.equals(e.getIsTargetPerson())) {
                        targetIds.add(e.getPersonId());
                        if (e.getPersonName() != null) tgtNameMap.put(e.getPersonId(), e.getPersonName());
                    }
                    if (e.getCameraId() != null) cams.add(e.getCameraId());
                    if (e.getLongitude() != null && e.getLatitude() != null) {
                        lngSum += e.getLongitudeDouble();
                        latSum += e.getLatitudeDouble();
                        coordCnt++;
                    }
                    if (start == null || e.getEventTime().isBefore(start)) start = e.getEventTime();
                    if (end == null || e.getEventTime().isAfter(end)) end = e.getEventTime();
                }

                AggregationAlertResult r = new AggregationAlertResult();
                r.setAlertId("CEP-AG" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 4));
                r.setAlertNo("CEP-AG" + System.currentTimeMillis());
                r.setAreaCode(area);
                r.setAreaName(area);
                r.setTotalPersonCount(personIds.size());
                r.setTargetPersonCount(targetIds.size());
                r.setPersonIds(new ArrayList<>(personIds));
                r.setPersonNames(new ArrayList<>(nameMap.values()));
                r.setTargetPersonIds(new ArrayList<>(targetIds));
                r.setTargetPersonNames(new ArrayList<>(tgtNameMap.values()));
                r.setCameraIds(new ArrayList<>(cams));
                if (coordCnt > 0) {
                    r.setCenterLongitude(BigDecimal.valueOf(lngSum / coordCnt));
                    r.setCenterLatitude(BigDecimal.valueOf(latSum / coordCnt));
                }
                r.setAlertLevel(Math.max(2, targetIds.size() + 1));
                r.setStartTime(start);
                r.setEndTime(end);
                r.setDurationSeconds(start != null && end != null ?
                        (int) java.time.Duration.between(start, end).getSeconds() : 0);
                r.setDescription(String.format("CEP匹配：区域[%s]检测到3人以上聚集（%d人，含%d名重点人员）",
                        area, personIds.size(), targetIds.size()));
                r.setTimestamp(System.currentTimeMillis());
                out.collect(r);
            } catch (Exception e) {
                log.error("CEP匹配处理失败：{}", e.getMessage());
            }
        }
    }

    public static class AggregationCooldownFunction
            extends KeyedProcessFunction<String, AggregationAlertResult, AggregationAlertResult> {

        private transient MapState<String, Long> lastAlertTimeMap;

        @Override
        public void open(Configuration parameters) {
            lastAlertTimeMap = getRuntimeContext().getMapState(
                    new MapStateDescriptor<>("aggregationCooldown", String.class, Long.class));
        }

        @Override
        public void processElement(AggregationAlertResult value, Context ctx,
                                   Collector<AggregationAlertResult> out) throws Exception {
            String dedupKey = value.getAreaCode() + "_" + value.getTotalPersonCount();
            Long lastAlert = lastAlertTimeMap.get(dedupKey);
            long now = System.currentTimeMillis();
            long cooldownMs = COOLDOWN_MINUTES * 60 * 1000L;
            if (lastAlert == null || (now - lastAlert) > cooldownMs) {
                lastAlertTimeMap.put(dedupKey, now);
                String globalKey = value.getAreaCode() + ":" + now / 1000;
                ALERT_COOLDOWN.add(globalKey);
                out.collect(value);
                log.warn("【聚集告警】输出告警：area={}, persons={}, targets={}, level={}",
                        value.getAreaCode(), value.getTotalPersonCount(),
                        value.getTargetPersonCount(), value.getAlertLevel());
            } else {
                log.debug("【聚集告警】冷却期跳过：area={}, lastAlert={}s前",
                        value.getAreaCode(), (now - lastAlert) / 1000);
            }
        }
    }

    public static class AggregationAlertRocketMQSink
            extends org.apache.flink.streaming.api.functions.sink.RichSinkFunction<AggregationAlertResult> {

        private final FlinkConfig flinkConfig;
        private transient org.apache.rocketmq.client.producer.DefaultMQProducer producer;

        public AggregationAlertRocketMQSink(FlinkConfig config) { this.flinkConfig = config; }

        @Override
        public void open(Configuration parameters) {
            try {
                producer = new org.apache.rocketmq.client.producer.DefaultMQProducer("flink-agg-sink-group");
                producer.setNamesrvAddr(flinkConfig.getNameServer());
                producer.start();
            } catch (Exception e) {
                log.error("RocketMQ Producer启动失败：{}", e.getMessage());
            }
        }

        @Override
        public void invoke(AggregationAlertResult value, Context context) {
            try {
                if (producer == null) return;
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "aggregation_alert");
                msg.put("data", value);
                msg.put("timestamp", System.currentTimeMillis());
                String json = JSON.toJSONString(msg);

                org.apache.rocketmq.common.message.Message mqMsg = new org.apache.rocketmq.common.message.Message(
                        flinkConfig.getWebSocketPushTopic(),
                        "aggregation_alert",
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                producer.sendOneway(mqMsg);

                org.apache.rocketmq.common.message.Message ctrlMsg = new org.apache.rocketmq.common.message.Message(
                        "police-control-topic",
                        "aggregation_alert",
                        JSON.toJSONString(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                producer.sendOneway(ctrlMsg);
            } catch (Exception e) {
                log.error("聚集告警发送MQ失败：{}", e.getMessage());
            }
        }

        @Override
        public void close() {
            if (producer != null) producer.shutdown();
        }
    }
}
