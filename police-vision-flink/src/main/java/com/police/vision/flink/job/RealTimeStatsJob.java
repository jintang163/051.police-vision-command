package com.police.vision.flink.job;

import com.police.vision.flink.entity.FlinkAlertEvent;
import com.police.vision.flink.entity.RealTimeStats;
import com.police.vision.flink.sink.RedisSink;
import com.police.vision.flink.sink.WebSocketPushSink;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.util.Collector;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RealTimeStatsJob {

    public static void run(org.apache.flink.streaming.api.environment.StreamExecutionEnvironment env,
                           String nameServer, String redisHost, int redisPort, String redisPassword) throws Exception {

        org.apache.flink.streaming.api.datastream.DataStream<RealTimeStats> statsStream = env
                .addSource(new RealTimeStatsSource(redisHost, redisPort, redisPassword))
                .name("RealTimeStats Source");

        statsStream.addSink(new RedisSink<>(
                redisHost, redisPort, redisPassword, "police:flink:"
        )).name("Redis Stats Sink");

        statsStream.addSink(new WebSocketPushSink<>(
                nameServer, "real_time_stats"
        )).name("WebSocket Stats Sink");

        statsStream.print("RealTime Stats");

        log.info("实时统计作业启动成功");
    }

    public static class RealTimeStatsSource implements SourceFunction<RealTimeStats> {

        private final String redisHost;
        private final int redisPort;
        private final String redisPassword;
        private transient JedisPool jedisPool;
        private volatile boolean running = true;

        public RealTimeStatsSource(String redisHost, int redisPort, String redisPassword) {
            this.redisHost = redisHost;
            this.redisPort = redisPort;
            this.redisPassword = redisPassword;
        }

        @Override
        public void run(SourceContext<RealTimeStats> ctx) throws Exception {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(5);
            poolConfig.setMaxIdle(2);
            poolConfig.setMaxWait(2000);

            if (redisPassword != null && !redisPassword.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword);
            } else {
                jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);
            }

            while (running) {
                try (Jedis jedis = jedisPool.getResource()) {
                    RealTimeStats stats = calculateStats(jedis);
                    ctx.collect(stats);
                } catch (Exception e) {
                    log.error("计算实时统计失败：{}", e.getMessage(), e);
                }
                Thread.sleep(5000);
            }
        }

        private RealTimeStats calculateStats(Jedis jedis) {
            RealTimeStats stats = new RealTimeStats(System.currentTimeMillis());

            String today = LocalDate.now().toString();

            String totalToday = jedis.get("police:alarm:count:today:" + today);
            stats.setTotalAlarmsToday(totalToday != null ? Long.parseLong(totalToday) : 0L);

            String pending = jedis.get("police:alarm:count:pending");
            stats.setPendingAlarms(pending != null ? Long.parseLong(pending) : 0L);

            String processing = jedis.get("police:alarm:count:processing");
            stats.setProcessingAlarms(processing != null ? Long.parseLong(processing) : 0L);

            String completed = jedis.get("police:alarm:count:completed:" + today);
            stats.setCompletedAlarmsToday(completed != null ? Long.parseLong(completed) : 0L);

            String onlinePolice = jedis.get("police:police:online:count");
            stats.setOnlinePoliceCount(onlinePolice != null ? Integer.parseInt(onlinePolice) : 0);

            String onlineCamera = jedis.get("police:camera:online:count");
            stats.setOnlineCameraCount(onlineCamera != null ? Integer.parseInt(onlineCamera) : 0);

            Map<Integer, Long> typeStats = new HashMap<>();
            for (int i = 1; i <= 6; i++) {
                String count = jedis.get("police:alarm:type:" + i + ":" + today);
                typeStats.put(i, count != null ? Long.parseLong(count) : 0L);
            }
            stats.setAlarmTypeStats(typeStats);

            Map<Integer, Long> levelStats = new HashMap<>();
            for (int i = 1; i <= 3; i++) {
                String count = jedis.get("police:alert:level:" + i + ":count");
                levelStats.put(i, count != null ? Long.parseLong(count) : 0L);
            }
            stats.setAlertLevelStats(levelStats);

            Map<String, Long> areaStats = new HashMap<>();
            areaStats.put("A区", 15L);
            areaStats.put("B区", 12L);
            areaStats.put("C区", 8L);
            stats.setAreaAlarmStats(areaStats);

            return stats;
        }

        @Override
        public void cancel() {
            running = false;
            if (jedisPool != null) {
                jedisPool.close();
            }
        }
    }
}
