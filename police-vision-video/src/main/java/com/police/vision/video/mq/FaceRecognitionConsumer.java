package com.police.vision.video.mq;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.video.entity.FaceRecord;
import com.police.vision.video.service.FaceRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.FACE_RECOGNITION_TOPIC,
        consumerGroup = "police-face-recognition-group",
        selectorExpression = MqConstant.TAG_FACE
)
public class FaceRecognitionConsumer implements RocketMQListener<String> {

    private final FaceRecognitionService faceRecognitionService;

    @Override
    public void onMessage(String message) {
        try {
            log.info("收到人脸识别消息：{}", message);
            Map<String, Object> faceData = JSON.parseObject(message, Map.class);
            if (faceData == null) {
                log.warn("人脸识别消息格式错误：{}", message);
                return;
            }
            boolean detected = (boolean) faceData.getOrDefault("detected", false);
            if (!detected) {
                log.debug("未检测到人脸，跳过处理");
                return;
            }
            String cameraId = (String) faceData.get("cameraId");
            byte[] image = null;
            if (faceData.containsKey("image")) {
                Object imgObj = faceData.get("image");
                if (imgObj instanceof String) {
                    image = java.util.Base64.getDecoder().decode((String) imgObj);
                } else if (imgObj instanceof byte[]) {
                    image = (byte[]) imgObj;
                }
            }
            if (image != null && image.length > 0) {
                processFaceImage(image, cameraId, faceData);
            } else {
                processFaceData(faceData);
            }
        } catch (Exception e) {
            log.error("处理人脸识别消息失败：", e);
        }
    }

    private void processFaceImage(byte[] image, String cameraId, Map<String, Object> faceData) {
        try {
            log.info("处理人脸图片，图片大小：{} bytes", image.length);
            float[] feature = faceRecognitionService.extractFaceFeature(image);
            float threshold = faceData.containsKey("threshold")
                    ? ((Number) faceData.get("threshold")).floatValue()
                    : 0.8f;
            List<Map<String, Object>> matches = faceRecognitionService.searchFaceByFeature(feature, threshold);
            if (!matches.isEmpty()) {
                for (Map<String, Object> match : matches) {
                    FaceRecord record = new FaceRecord();
                    record.setCameraId(cameraId);
                    record.setPersonId((String) match.get("person_id"));
                    record.setPersonName((String) match.get("person_name"));
                    record.setSimilarity(BigDecimal.valueOf(((Number) match.get("similarity")).doubleValue() * 100));
                    record.setLongitude(faceData.containsKey("longitude")
                            ? new BigDecimal(faceData.get("longitude").toString())
                            : null);
                    record.setLatitude(faceData.containsKey("latitude")
                            ? new BigDecimal(faceData.get("latitude").toString())
                            : null);
                    record.setSnapshotUrl((String) faceData.get("snapshotUrl"));
                    faceRecognitionService.handleFaceMatch(record);
                    log.info("人脸匹配成功：personId={}, similarity={}",
                            record.getPersonId(), record.getSimilarity());
                }
            } else {
                FaceRecord record = new FaceRecord();
                record.setCameraId(cameraId);
                record.setSnapshotUrl((String) faceData.get("snapshotUrl"));
                record.setLongitude(faceData.containsKey("longitude")
                        ? new BigDecimal(faceData.get("longitude").toString())
                        : null);
                record.setLatitude(faceData.containsKey("latitude")
                        ? new BigDecimal(faceData.get("latitude").toString())
                        : null);
                faceRecognitionService.saveFaceRecord(record);
                log.info("保存未匹配的人脸记录");
            }
        } catch (Exception e) {
            log.error("处理人脸图片失败：", e);
        }
    }

    private void processFaceData(Map<String, Object> faceData) {
        try {
            log.info("处理人脸数据：{}", faceData);
            FaceRecord record = new FaceRecord();
            record.setCameraId((String) faceData.get("cameraId"));
            record.setPersonId((String) faceData.get("personId"));
            record.setPersonName((String) faceData.get("personName"));
            if (faceData.containsKey("similarity")) {
                record.setSimilarity(BigDecimal.valueOf(((Number) faceData.get("similarity")).doubleValue()));
            }
            record.setLongitude(faceData.containsKey("longitude")
                    ? new BigDecimal(faceData.get("longitude").toString())
                    : null);
            record.setLatitude(faceData.containsKey("latitude")
                    ? new BigDecimal(faceData.get("latitude").toString())
                    : null);
            record.setSnapshotUrl((String) faceData.get("snapshotUrl"));
            if (record.getPersonId() != null) {
                faceRecognitionService.handleFaceMatch(record);
            } else {
                faceRecognitionService.saveFaceRecord(record);
            }
        } catch (Exception e) {
            log.error("处理人脸数据失败：", e);
        }
    }
}
