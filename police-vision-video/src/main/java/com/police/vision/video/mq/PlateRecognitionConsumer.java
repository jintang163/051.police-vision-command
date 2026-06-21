package com.police.vision.video.mq;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.video.entity.PlateRecord;
import com.police.vision.video.service.PlateRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.PLATE_RECOGNITION_TOPIC,
        consumerGroup = "police-plate-recognition-group",
        selectorExpression = MqConstant.TAG_PLATE
)
public class PlateRecognitionConsumer implements RocketMQListener<String> {

    private final PlateRecognitionService plateRecognitionService;

    @Override
    public void onMessage(String message) {
        try {
            log.info("收到车牌识别消息：{}", message);
            Map<String, Object> plateData = JSON.parseObject(message, Map.class);
            if (plateData == null) {
                log.warn("车牌识别消息格式错误：{}", message);
                return;
            }
            boolean detected = (boolean) plateData.getOrDefault("detected", false);
            if (!detected) {
                log.debug("未检测到车牌，跳过处理");
                return;
            }
            String cameraId = (String) plateData.get("cameraId");
            byte[] image = null;
            if (plateData.containsKey("image")) {
                Object imgObj = plateData.get("image");
                if (imgObj instanceof String) {
                    image = java.util.Base64.getDecoder().decode((String) imgObj);
                } else if (imgObj instanceof byte[]) {
                    image = (byte[]) imgObj;
                }
            }
            if (image != null && image.length > 0) {
                processPlateImage(image, cameraId, plateData);
            } else {
                processPlateData(plateData);
            }
        } catch (Exception e) {
            log.error("处理车牌识别消息失败：", e);
        }
    }

    private void processPlateImage(byte[] image, String cameraId, Map<String, Object> plateData) {
        try {
            log.info("处理车牌图片，图片大小：{} bytes", image.length);
            Map<String, Object> result = plateRecognitionService.recognizePlate(image);
            String plateNo = (String) result.get("plateNo");
            if (plateNo != null && !plateNo.isEmpty()) {
                PlateRecord record = new PlateRecord();
                record.setCameraId(cameraId);
                record.setPlateNo(plateNo);
                record.setVehicleColor((String) result.get("vehicleColor"));
                record.setVehicleType((String) result.get("vehicleType"));
                record.setLongitude(plateData.containsKey("longitude")
                        ? new BigDecimal(plateData.get("longitude").toString())
                        : null);
                record.setLatitude(plateData.containsKey("latitude")
                        ? new BigDecimal(plateData.get("latitude").toString())
                        : null);
                record.setSnapshotUrl((String) plateData.get("snapshotUrl"));
                boolean valid = (boolean) result.getOrDefault("valid", false);
                if (valid) {
                    plateRecognitionService.handlePlateMatch(record);
                    log.info("车牌识别并处理完成：plateNo={}", plateNo);
                } else {
                    plateRecognitionService.savePlateRecord(record);
                    log.info("保存无效车牌记录：plateNo={}", plateNo);
                }
            } else {
                log.warn("未从图片中识别到有效车牌");
            }
        } catch (Exception e) {
            log.error("处理车牌图片失败：", e);
        }
    }

    private void processPlateData(Map<String, Object> plateData) {
        try {
            log.info("处理车牌数据：{}", plateData);
            String plateNo = (String) plateData.get("plateNo");
            if (plateNo == null || plateNo.isEmpty()) {
                log.warn("车牌数据中没有车牌号");
                return;
            }
            PlateRecord record = new PlateRecord();
            record.setCameraId((String) plateData.get("cameraId"));
            record.setPlateNo(plateNo);
            record.setVehicleColor((String) plateData.get("vehicleColor"));
            record.setVehicleType((String) plateData.get("vehicleType"));
            record.setLongitude(plateData.containsKey("longitude")
                    ? new BigDecimal(plateData.get("longitude").toString())
                    : null);
            record.setLatitude(plateData.containsKey("latitude")
                    ? new BigDecimal(plateData.get("latitude").toString())
                    : null);
            record.setSnapshotUrl((String) plateData.get("snapshotUrl"));
            boolean valid = plateRecognitionService.validatePlate(plateNo);
            if (valid) {
                plateRecognitionService.handlePlateMatch(record);
                log.info("车牌数据处理完成：plateNo={}", plateNo);
            } else {
                plateRecognitionService.savePlateRecord(record);
                log.info("保存无效格式车牌记录：plateNo={}", plateNo);
            }
        } catch (Exception e) {
            log.error("处理车牌数据失败：", e);
        }
    }
}
