package com.police.vision.flink.job;

import com.police.vision.common.constant.MqConstant;
import com.police.vision.flink.entity.FlinkAlertEvent;
import com.police.vision.flink.entity.HeatmapPoint;
import com.police.vision.flink.schema.AlertEventSchema;
import com.police.vision.flink.sink.RedisSink;
import com.police.vision.flink.util.GridUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.rocketmq.source.RocketMQSource;
import org.apache.flink.connector.rocketmq.source.reader.deserializer.RocketMQDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.time.Duration;

@Slf4j
public class HeatmapCalculationJob {

    public static void run(StreamExecutionEnvironment env, String nameServer,
                           String redisHost, int redisPort, String redisPassword) throws Exception {

        RocketMQSource<FlinkAlertEvent> source = RocketMQSource.<FlinkAlertEvent>builder()
                .setNameServerAddress(nameServer)
                .setTopic(MqConstant.VIDEO_ANALYSIS_TOPIC)
                .setGroupId("police-flink-heatmap-group")
                .setDeserializer(RocketMQDeserializationSchema.valueOnly(new AlertEventSchema()))
                .build();

        DataStream<FlinkAlertEvent> alertStream = env.fromSource(
                source,
                WatermarkStrategy.<FlinkAlertEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((event, timestamp) -> event.getEventTimestamp()),
                "RocketMQ Heatmap Source"
        );

        DataStream<HeatmapPoint> heatmapStream = alertStream
                .filter(event -> event != null && event.getLongitude() != null && event.getLatitude() != null)
                .map(event -> {
                    String gridCode = GridUtil.getGridCode(event.getLongitude(), event.getLatitude());
                    return new HeatmapPoint(
                            event.getLongitude(),
                            event.getLatitude(),
                            getWeightByLevel(event.getAlertLevel()),
                            gridCode,
                            event.getEventTimestamp(),
                            event.getAlertType()
                    );
                });

        DataStream<HeatmapPoint> aggregatedHeatmap = heatmapStream
                .keyBy(HeatmapPoint::getGridCode)
                .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.seconds(30)))
                .reduce((a, b) -> {
                    HeatmapPoint result = new HeatmapPoint();
                    result.setGridCode(a.getGridCode());
                    result.setLongitude(a.getLongitude());
                    result.setLatitude(a.getLatitude());
                    result.setWeight(a.getWeight() + b.getWeight());
                    result.setTimestamp(Math.max(a.getTimestamp(), b.getTimestamp()));
                    result.setDataType(a.getDataType());
                    return result;
                });

        aggregatedHeatmap.addSink(new RedisSink<>(
                redisHost, redisPort, redisPassword, "police:flink:"
        )).name("Redis Heatmap Sink");

        aggregatedHeatmap.print("Heatmap Point");

        log.info("热力图计算作业启动成功");
    }

    private static int getWeightByLevel(Integer level) {
        if (level == null) return 1;
        return switch (level) {
            case 1 -> 5;
            case 2 -> 3;
            case 3 -> 1;
            default -> 1;
        };
    }
}
