package com.police.vision.traffic.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.entity.TrafficCaptureData;
import com.police.vision.common.entity.VehicleControlAlert;
import com.police.vision.traffic.service.VehicleControlService;
import com.police.vision.traffic.service.VehicleTrackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrafficCaptureConsumer {

    private final VehicleControlService vehicleControlService;
    private final VehicleTrackService vehicleTrackService;

    @KafkaListener(topics = MqConstant.KAFKA_TOPIC_TRAFFIC_CAPTURE,
            groupId = MqConstant.KAFKA_CONSUMER_GROUP_TRAFFIC,
            concurrency = "3")
    public void onMessage(List<ConsumerRecord<String, String>> records, Acknowledgment ack,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        int count = 0;
        try {
            for (ConsumerRecord<String, String> record : records) {
                try {
                    processMessage(record.value());
                    count++;
                } catch (Exception e) {
                    log.error("处理卡口数据失败：partition={}, offset={}, error={}",
                            record.partition(), record.offset(), e.getMessage());
                }
            }
            log.info("批量处理卡口数据完成：数量={}, topic={}", count, topic);
        } finally {
            ack.acknowledge();
        }
    }

    @SentinelResource(value = "traffic_capture_consume", blockHandler = "handleConsumeBlock")
    private void processMessage(String message) {
        try {
            TrafficCaptureData captureData = JSON.parseObject(message, TrafficCaptureData.class);
            if (captureData == null || captureData.getPlateNo() == null || captureData.getPlateNo().isEmpty()) {
                log.debug("卡口数据无效，跳过：{}", message);
                return;
            }

            List<VehicleControlAlert> alerts = vehicleControlService.checkAndHandleControl(captureData);

            vehicleTrackService.storeTrackPoint(captureData);

            log.debug("卡口数据处理完成：plateNo={}, crossing={}, alerts={}",
                    captureData.getPlateNo(), captureData.getCrossingName(), alerts.size());
        } catch (Exception e) {
            log.error("处理卡口数据异常：", e);
            throw e;
        }
    }

    public void handleConsumeBlock(String message, BlockException ex) {
        log.warn("卡口数据消费触发Sentinel限流：rule={}", ex.getRule().getResource());
        try {
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
