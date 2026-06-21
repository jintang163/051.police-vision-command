package com.police.vision.flink.job;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.flink.config.FlinkConfig;
import com.police.vision.flink.entity.EventTrafficCapture;
import com.police.vision.flink.schema.EventTrafficCaptureSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.rocketmq.source.RocketMQSource;
import org.apache.flink.connector.rocketmq.source.RocketMQSourceOptions;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventTrafficMonitorJob implements CommandLineRunner {

    private final FlinkConfig flinkConfig;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: EventTrafficMonitorJob <eventId> <pedestrianThreshold> <vehicleThreshold> <windowSeconds>");
            System.exit(1);
        }

        Long eventId = Long.parseLong(args[0]);
        Long pedestrianThreshold = Long.parseLong(args[1]);
        Long vehicleThreshold = Long.parseLong(args[2]);
        int windowSeconds = Integer.parseInt(args[3]);

        runJob(eventId, pedestrianThreshold, vehicleThreshold, windowSeconds);
    }

    public static void runJob(Long eventId, Long pedestrianThreshold, Long vehicleThreshold, int windowSeconds) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        String nameServer = System.getProperty("rocketmq.nameserver", "127.0.0.1:9876");
        String redisHost = System.getProperty("redis.host", "127.0.0.1");
        int redisPort = Integer.parseInt(System.getProperty("redis.port", "6379"));
        String redisPassword = System.getProperty("redis.password", "");

        RocketMQSource<EventTrafficCapture> source = RocketMQSource.<EventTrafficCapture>builder()
                .setTopicName(MqConstant.EVENT_TRAFFIC_DATA_TOPIC)
                .setConsumerGroup("flink-event-traffic-monitor-group-" + eventId)
                .setNameServerAddress(nameServer)
                .setStartMessageOffset(RocketMQSourceOptions.START_LATEST)
                .setDeserializationSchema(new EventTrafficCaptureSchema())
                .build();

        DataStream<EventTrafficCapture> trafficStream = env
                .fromSource(source, WatermarkStrategy
                                .<EventTrafficCapture>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((event, ts) -> event.getCaptureTime() != null ? event.getCaptureTime() : System.currentTimeMillis()),
                        "EventTrafficCaptureSource"
                )
                .name("EventTrafficCaptureSource")
                .filter(e -> e.getType() != null && e.getCount() != null && e.getEventId() != null)
                .name("FilterValidTraffic");

        SingleOutputStreamOperator<TrafficAlertResult> alertStream = trafficStream
                .keyBy(capture -> capture.getEventId() + "_" + capture.getType())
                .window(TumblingProcessingTimeWindows.of(Time.seconds(windowSeconds)))
                .aggregate(new TrafficCountAggregate(), new TrafficAlertProcessWindowFunction(pedestrianThreshold, vehicleThreshold))
                .name("TrafficAlertDetection");

        alertStream.addSink(new TrafficAlertRocketMQSink(nameServer, MqConstant.EVENT_TRAFFIC_ALERT_TOPIC))
                .name("TrafficAlertRocketMQSink");

        alertStream.addSink(new TrafficCountRedisSink(redisHost, redisPort, redisPassword))
                .name("TrafficCountRedisSink");

        alertStream.print("EventTrafficAlert");

        env.executeAsync("EventTrafficMonitor-Job-" + eventId);
        log.info("活动交通监控Flink任务已启动：eventId={}, 行人阈值={}, 车辆阈值={}, 窗口={}秒",
                eventId, pedestrianThreshold, vehicleThreshold, windowSeconds);
    }

    @Override
    public void run(String... args) {
        try {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(15000L);
                    log.info("活动交通监控任务等待手动启动，通过main方法或API触发具体eventId的任务");
                } catch (Exception e) {
                    log.error("活动交通监控任务线程异常：{}", e.getMessage(), e);
                }
            });
            thread.setDaemon(true);
            thread.setName("Flink-Event-Traffic-Monitor");
            thread.start();
        } catch (Exception e) {
            log.warn("活动交通监控任务启动线程创建失败：{}", e.getMessage());
        }
    }

    public static class TrafficCountAggregate implements AggregateFunction<EventTrafficCapture, TrafficAccumulator, TrafficAccumulator> {

        @Override
        public TrafficAccumulator createAccumulator() {
            return new TrafficAccumulator();
        }

        @Override
        public TrafficAccumulator add(EventTrafficCapture value, TrafficAccumulator accumulator) {
            accumulator.setEventId(value.getEventId());
            accumulator.setType(value.getType());
            accumulator.setCount(accumulator.getCount() + (value.getCount() != null ? value.getCount() : 0L));
            if (accumulator.getLng() == null && value.getLng() != null) {
                accumulator.setLng(value.getLng());
            }
            if (accumulator.getLat() == null && value.getLat() != null) {
                accumulator.setLat(value.getLat());
            }
            return accumulator;
        }

        @Override
        public TrafficAccumulator getResult(TrafficAccumulator accumulator) {
            return accumulator;
        }

        @Override
        public TrafficAccumulator merge(TrafficAccumulator a, TrafficAccumulator b) {
            TrafficAccumulator merged = new TrafficAccumulator();
            merged.setEventId(a.getEventId() != null ? a.getEventId() : b.getEventId());
            merged.setType(a.getType() != null ? a.getType() : b.getType());
            merged.setCount(a.getCount() + b.getCount());
            merged.setLng(a.getLng() != null ? a.getLng() : b.getLng());
            merged.setLat(a.getLat() != null ? a.getLat() : b.getLat());
            return merged;
        }
    }

    @lombok.Data
    public static class TrafficAccumulator implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long eventId;
        private String type;
        private Long count = 0L;
        private Double lng;
        private Double lat;
    }

    public static class TrafficAlertProcessWindowFunction
            extends ProcessWindowFunction<TrafficAccumulator, TrafficAlertResult, String, TimeWindow> {

        private final Long pedestrianThreshold;
        private final Long vehicleThreshold;

        public TrafficAlertProcessWindowFunction(Long pedestrianThreshold, Long vehicleThreshold) {
            this.pedestrianThreshold = pedestrianThreshold;
            this.vehicleThreshold = vehicleThreshold;
        }

        @Override
        public void process(String key, Context context, Iterable<TrafficAccumulator> elements, Collector<TrafficAlertResult> out) {
            for (TrafficAccumulator acc : elements) {
                String type = acc.getType();
                Long totalCount = acc.getCount();
                Long eventId = acc.getEventId();

                Long threshold = "pedestrian".equals(type) ? pedestrianThreshold : vehicleThreshold;
                if (threshold == null || threshold <= 0) {
                    continue;
                }

                if (totalCount > threshold) {
                    double exceedRatio = (double) (totalCount - threshold) / threshold;
                    int alertLevel;
                    if (exceedRatio >= 0.5) {
                        alertLevel = 3;
                    } else if (exceedRatio >= 0.3) {
                        alertLevel = 2;
                    } else if (exceedRatio >= 0.1) {
                        alertLevel = 1;
                    } else {
                        continue;
                    }

                    TrafficAlertResult alert = new TrafficAlertResult();
                    alert.setEventId(eventId);
                    alert.setAlertType(type);
                    alert.setAlertLevel(alertLevel);
                    alert.setCountValue(totalCount);
                    alert.setThresholdValue(threshold);
                    alert.setLng(acc.getLng());
                    alert.setLat(acc.getLat());
                    alert.setAlertTime(System.currentTimeMillis());

                    log.warn("【交通预警】触发预警：type={}, count={}, threshold={}, level={}, eventId={}",
                            type, totalCount, threshold, alertLevel, eventId);
                    out.collect(alert);
                }
            }
        }
    }

    @lombok.Data
    public static class TrafficAlertResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long eventId;
        private String alertType;
        private Integer alertLevel;
        private Long countValue;
        private Long thresholdValue;
        private Double lng;
        private Double lat;
        private Long alertTime;
    }

    public static class TrafficAlertRocketMQSink extends RichSinkFunction<TrafficAlertResult> {

        private final String nameServer;
        private final String topic;
        private transient DefaultMQProducer producer;

        public TrafficAlertRocketMQSink(String nameServer, String topic) {
            this.nameServer = nameServer;
            this.topic = topic;
        }

        @Override
        public void open(Configuration parameters) {
            try {
                producer = new DefaultMQProducer("flink-traffic-alert-sink-group");
                producer.setNamesrvAddr(nameServer);
                producer.start();
            } catch (Exception e) {
                log.error("RocketMQ Producer启动失败：{}", e.getMessage());
            }
        }

        @Override
        public void invoke(TrafficAlertResult value, Context context) {
            try {
                if (producer == null) return;
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", MqConstant.TAG_EVENT_TRAFFIC_ALERT);
                msg.put("data", value);
                msg.put("timestamp", System.currentTimeMillis());
                String json = JSON.toJSONString(msg);

                Message mqMsg = new Message(
                        topic,
                        MqConstant.TAG_EVENT_TRAFFIC_ALERT,
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                producer.sendOneway(mqMsg);
            } catch (Exception e) {
                log.error("交通预警发送MQ失败：{}", e.getMessage());
            }
        }

        @Override
        public void close() {
            if (producer != null) producer.shutdown();
        }
    }

    public static class TrafficCountRedisSink extends RichSinkFunction<TrafficAlertResult> {

        private final String redisHost;
        private final int redisPort;
        private final String redisPassword;
        private transient JedisPool jedisPool;

        public TrafficCountRedisSink(String redisHost, int redisPort, String redisPassword) {
            this.redisHost = redisHost;
            this.redisPort = redisPort;
            this.redisPassword = redisPassword;
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(2);
            poolConfig.setMaxWaitMillis(3000);

            if (redisPassword != null && !redisPassword.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 3000, redisPassword);
            } else {
                jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 3000);
            }
        }

        @Override
        public void invoke(TrafficAlertResult value, Context context) throws Exception {
            try (Jedis jedis = jedisPool.getResource()) {
                String type = value.getAlertType();
                Long count = value.getCountValue();
                Long eventId = value.getEventId();
                if ("pedestrian".equals(type)) {
                    String key = "event:" + eventId + ":pedestrian:total";
                    jedis.incrBy(key, count != null ? count : 0L);
                } else if ("vehicle".equals(type)) {
                    String key = "event:" + eventId + ":vehicle:total";
                    jedis.incrBy(key, count != null ? count : 0L);
                }
                log.debug("写入Redis交通统计成功：eventId={}, type={}, count={}", eventId, type, count);
            } catch (Exception e) {
                log.error("写入Redis交通统计失败：{}", e.getMessage(), e);
            }
        }

        @Override
        public void close() throws Exception {
            if (jedisPool != null) {
                jedisPool.close();
            }
        }
    }
}
