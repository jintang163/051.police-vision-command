package com.police.vision.flink.job;

import com.police.vision.common.constant.MqConstant;
import com.police.vision.flink.entity.AlertAggregationResult;
import com.police.vision.flink.entity.FlinkAlertEvent;
import com.police.vision.flink.schema.AlertEventSchema;
import com.police.vision.flink.sink.RedisSink;
import com.police.vision.flink.sink.WebSocketPushSink;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.connector.rocketmq.source.RocketMQSource;
import org.apache.flink.connector.rocketmq.source.reader.deserializer.RocketMQDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class AlertAggregationJob {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void run(StreamExecutionEnvironment env, String nameServer,
                           String redisHost, int redisPort, String redisPassword) throws Exception {

        RocketMQSource<FlinkAlertEvent> source = RocketMQSource.<FlinkAlertEvent>builder()
                .setNameServerAddress(nameServer)
                .setTopic(MqConstant.VIDEO_ANALYSIS_TOPIC)
                .setGroupId("police-flink-alert-group")
                .setDeserializer(RocketMQDeserializationSchema.valueOnly(new AlertEventSchema()))
                .build();

        DataStream<FlinkAlertEvent> alertStream = env.fromSource(
                        source,
                        WatermarkStrategy.<FlinkAlertEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((event, timestamp) -> event.getEventTimestamp()),
                        "RocketMQ Alert Source")
                .filter(event -> event != null && event.getAlertType() != null);

        KeyedStream<FlinkAlertEvent, Integer> keyedStream = alertStream
                .keyBy((KeySelector<FlinkAlertEvent, Integer>) FlinkAlertEvent::getAlertType);

        DataStream<AlertAggregationResult> aggregationStream = keyedStream
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .aggregate(
                        new AlertAggregateFunction(),
                        (Integer key, TimeWindow window, Iterable<AlertAggregationResult> input, Collector<AlertAggregationResult> out) -> {
                            AlertAggregationResult result = input.iterator().next();
                            result.setWindowStart(LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(window.getStart()),
                                    ZoneId.systemDefault()).format(FORMATTER));
                            result.setWindowEnd(LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(window.getEnd()),
                                    ZoneId.systemDefault()).format(FORMATTER));
                            result.setWindowTimestamp(window.getEnd());
                            result.setAlertTypeName(getAlertTypeName(key));
                            out.collect(result);
                        }
                );

        aggregationStream.addSink(new RedisSink<>(
                redisHost, redisPort, redisPassword, "police:flink:"
        )).name("Redis Aggregation Sink");

        aggregationStream.addSink(new WebSocketPushSink<>(
                nameServer, "alert_aggregation"
        )).name("WebSocket Push Sink");

        aggregationStream.print("Alert Aggregation Result");

        log.info("告警聚合作业启动成功");
    }

    private static String getAlertTypeName(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case 1 -> "重点人员布控";
            case 2 -> "车牌识别";
            case 3 -> "打架斗殴";
            case 4 -> "人群聚集";
            case 5 -> "异常行为";
            default -> "其他";
        };
    }

    public static class AlertAggregateFunction implements AggregateFunction<FlinkAlertEvent, AlertAggregationResult, AlertAggregationResult> {

        @Override
        public AlertAggregationResult createAccumulator() {
            return new AlertAggregationResult(0, 0L);
        }

        @Override
        public AlertAggregationResult add(FlinkAlertEvent value, AlertAggregationResult accumulator) {
            accumulator.setAlertType(value.getAlertType());
            accumulator.setCount(accumulator.getCount() + 1);

            if (accumulator.getLevel1Count() == null) accumulator.setLevel1Count(0);
            if (accumulator.getLevel2Count() == null) accumulator.setLevel2Count(0);
            if (accumulator.getLevel3Count() == null) accumulator.setLevel3Count(0);

            if (value.getAlertLevel() != null) {
                switch (value.getAlertLevel()) {
                    case 1 -> accumulator.setLevel1Count(accumulator.getLevel1Count() + 1);
                    case 2 -> accumulator.setLevel2Count(accumulator.getLevel2Count() + 1);
                    case 3 -> accumulator.setLevel3Count(accumulator.getLevel3Count() + 1);
                }
            }
            return accumulator;
        }

        @Override
        public AlertAggregationResult getResult(AlertAggregationResult accumulator) {
            return accumulator;
        }

        @Override
        public AlertAggregationResult merge(AlertAggregationResult a, AlertAggregationResult b) {
            AlertAggregationResult merged = new AlertAggregationResult();
            merged.setAlertType(a.getAlertType());
            merged.setCount(a.getCount() + b.getCount());
            merged.setLevel1Count((a.getLevel1Count() != null ? a.getLevel1Count() : 0) +
                    (b.getLevel1Count() != null ? b.getLevel1Count() : 0));
            merged.setLevel2Count((a.getLevel2Count() != null ? a.getLevel2Count() : 0) +
                    (b.getLevel2Count() != null ? b.getLevel2Count() : 0));
            merged.setLevel3Count((a.getLevel3Count() != null ? a.getLevel3Count() : 0) +
                    (b.getLevel3Count() != null ? b.getLevel3Count() : 0));
            return merged;
        }
    }
}
