package com.police.vision.flink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "flink.job")
public class FlinkConfig {

    private String name = "PoliceVisionFlinkJob";
    private int parallelism = 4;
    private String nameServer = "127.0.0.1:9876";
    private String faceCaptureTopic = "police-face-recognition-topic";
    private String websocketPushTopic = "police-websocket-push-topic";
    private CheckpointConfig checkpoint = new CheckpointConfig();
    private StateConfig state = new StateConfig();
    private KafkaConfig kafka = new KafkaConfig();

    @Data
    public static class CheckpointConfig {
        private boolean enabled = true;
        private long interval = 60000;
        private long timeout = 300000;
    }

    @Data
    public static class StateConfig {
        private String backend = "filesystem";
        private String path = "hdfs://localhost:9000/flink/checkpoints";
    }

    @Data
    public static class KafkaConfig {
        private String bootstrapServers = "127.0.0.1:9092";
        private String trafficCaptureTopic = "traffic-capture-topic";
        private String vehicleControlAlertTopic = "vehicle-control-alert-topic";
        private String consumerGroup = "flink-traffic-consumer-group";
    }
}
