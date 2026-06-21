package com.police.vision.flink.sink;

import com.alibaba.fastjson2.JSON;
import com.police.vision.flink.entity.AlertAggregationResult;
import com.police.vision.flink.entity.HeatmapPoint;
import com.police.vision.flink.entity.RealTimeStats;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisSink<T> extends RichSinkFunction<T> {

    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final String keyPrefix;
    private transient JedisPool jedisPool;

    public RedisSink(String redisHost, int redisPort, String redisPassword, String keyPrefix) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(2);
        poolConfig.setMaxWait(3000);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 3000, redisPassword);
        } else {
            jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 3000);
        }
    }

    @Override
    public void invoke(T value, Context context) throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            String key;
            String jsonValue = JSON.toJSONString(value);

            if (value instanceof AlertAggregationResult result) {
                key = keyPrefix + "aggregation:" + result.getAlertType() + ":" + result.getWindowTimestamp();
                jedis.setex(key, (int) TimeUnit.HOURS.toSeconds(2), jsonValue);

                String latestKey = keyPrefix + "aggregation:latest:" + result.getAlertType();
                jedis.set(latestKey, jsonValue);
            } else if (value instanceof HeatmapPoint point) {
                key = keyPrefix + "heatmap:" + point.getGridCode() + ":" + point.getTimestamp();
                jedis.setex(key, (int) TimeUnit.HOURS.toSeconds(1), jsonValue);

                String heatmapKey = keyPrefix + "heatmap:current";
                jedis.zadd(heatmapKey, point.getWeight(),
                        point.getLongitude() + "," + point.getLatitude() + ":" + point.getTimestamp());
                jedis.zremrangeByRank(heatmapKey, 0, -1001);
            } else if (value instanceof RealTimeStats stats) {
                key = keyPrefix + "stats:realtime";
                jedis.set(key, jsonValue);
            } else {
                key = keyPrefix + "generic:" + System.currentTimeMillis();
                jedis.setex(key, (int) TimeUnit.HOURS.toSeconds(1), jsonValue);
            }

            log.debug("写入Redis成功：key={}", key);
        } catch (Exception e) {
            log.error("写入Redis失败：{}", e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
