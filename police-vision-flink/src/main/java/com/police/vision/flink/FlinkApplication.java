package com.police.vision.flink;

import com.police.vision.flink.config.FlinkConfig;
import com.police.vision.flink.job.AlertAggregationJob;
import com.police.vision.flink.job.HeatmapCalculationJob;
import com.police.vision.flink.job.RealTimeStatsJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
@EnableConfigurationProperties(FlinkConfig.class)
public class FlinkApplication implements CommandLineRunner {

    private final FlinkConfig flinkConfig;

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    @Value("${redis.password:}")
    private String redisPassword;

    public static void main(String[] args) {
        SpringApplication.run(FlinkApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("正在启动Flink流处理作业...");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(flinkConfig.getParallelism());

        env.getConfig().setRestartStrategy(
                RestartStrategies.fixedDelayRestart(3, 10000L)
        );

        if (flinkConfig.getCheckpoint().isEnabled()) {
            env.enableCheckpointing(
                    flinkConfig.getCheckpoint().getInterval(),
                    CheckpointingMode.EXACTLY_ONCE
            );
            env.getCheckpointConfig().setCheckpointTimeout(
                    flinkConfig.getCheckpoint().getTimeout()
            );
            env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        }

        if ("rocksdb".equalsIgnoreCase(flinkConfig.getState().getBackend())) {
            env.setStateBackend(new EmbeddedRocksDBStateBackend());
        } else if ("filesystem".equalsIgnoreCase(flinkConfig.getState().getBackend())) {
            env.setStateBackend(new FsStateBackend(flinkConfig.getState().getPath()));
        }

        AlertAggregationJob.run(env, nameServer, redisHost, redisPort, redisPassword);
        HeatmapCalculationJob.run(env, nameServer, redisHost, redisPort, redisPassword);
        RealTimeStatsJob.run(env, nameServer, redisHost, redisPort, redisPassword);

        log.info("Flink作业提交执行中...");
        env.execute(flinkConfig.getName());
    }
}
