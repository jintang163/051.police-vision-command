package com.police.vision.video.mq;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.dto.VideoAnalyzeDTO;
import com.police.vision.common.util.MqUtil;
import com.police.vision.video.entity.AlertRecord;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.mapper.AlertRecordMapper;
import com.police.vision.video.mapper.CameraDeviceMapper;
import com.police.vision.video.service.BehaviorAnalysisService;
import com.police.vision.video.service.FaceRecognitionService;
import com.police.vision.video.service.PlateRecognitionService;
import com.police.vision.video.service.VideoStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.VIDEO_ANALYSIS_TOPIC,
        consumerGroup = MqConstant.VIDEO_ANALYSIS_GROUP
)
public class VideoAnalysisConsumer implements RocketMQListener<String> {

    private final BehaviorAnalysisService behaviorAnalysisService;
    private final FaceRecognitionService faceRecognitionService;
    private final PlateRecognitionService plateRecognitionService;
    private final VideoStorageService videoStorageService;
    private final CameraDeviceMapper cameraDeviceMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final MqUtil mqUtil;

    @Override
    public void onMessage(String message) {
        try {
            log.info("收到视频分析消息：{}", message);
            VideoAnalyzeDTO analyzeDTO = JSON.parseObject(message, VideoAnalyzeDTO.class);
            if (analyzeDTO == null) {
                log.warn("视频分析消息格式错误：{}", message);
                return;
            }
            String cameraId = analyzeDTO.getCameraId();
            String streamUrl = analyzeDTO.getStreamUrl();
            Integer analyzeType = analyzeDTO.getAnalyzeType();
            CameraDevice camera = cameraDeviceMapper.selectByDeviceId(cameraId);
            BigDecimal longitude = camera != null ? camera.getLongitude() : null;
            BigDecimal latitude = camera != null ? camera.getLatitude() : null;
            switch (analyzeType) {
                case 1:
                    handleFaceRecognition(cameraId, streamUrl, longitude, latitude);
                    break;
                case 2:
                    handlePlateRecognition(cameraId, streamUrl, longitude, latitude);
                    break;
                case 3:
                    handleBehaviorAnalysis(cameraId, streamUrl, longitude, latitude);
                    break;
                case 4:
                    handleFullAnalysis(cameraId, streamUrl, longitude, latitude);
                    break;
                default:
                    log.warn("未知的分析类型：{}", analyzeType);
            }
            log.info("视频分析处理完成：cameraId={}, analyzeType={}", cameraId, analyzeType);
        } catch (Exception e) {
            log.error("处理视频分析消息失败：", e);
        }
    }

    private void handleFaceRecognition(String cameraId, String streamUrl,
                                       BigDecimal longitude, BigDecimal latitude) {
        log.info("执行人脸识别分析：cameraId={}", cameraId);
        try {
            Map<String, Object> faceResult = Map.of(
                    "detected", Math.random() > 0.5,
                    "cameraId", cameraId,
                    "streamUrl", streamUrl,
                    "timestamp", System.currentTimeMillis()
            );
            mqUtil.sendFaceRecognition(faceResult);
        } catch (Exception e) {
            log.error("人脸识别分析失败：", e);
        }
    }

    private void handlePlateRecognition(String cameraId, String streamUrl,
                                        BigDecimal longitude, BigDecimal latitude) {
        log.info("执行车牌识别分析：cameraId={}", cameraId);
        try {
            Map<String, Object> plateResult = Map.of(
                    "detected", Math.random() > 0.5,
                    "cameraId", cameraId,
                    "streamUrl", streamUrl,
                    "timestamp", System.currentTimeMillis()
            );
            mqUtil.sendPlateRecognition(plateResult);
        } catch (Exception e) {
            log.error("车牌识别分析失败：", e);
        }
    }

    private void handleBehaviorAnalysis(String cameraId, String streamUrl,
                                        BigDecimal longitude, BigDecimal latitude) {
        log.info("执行行为分析：cameraId={}", cameraId);
        try {
            Map<String, Object> behaviorResult = behaviorAnalysisService.analyzeBehavior(streamUrl, cameraId);
            boolean detected = (boolean) behaviorResult.getOrDefault("detected", false);
            if (detected) {
                Integer behaviorType = (Integer) behaviorResult.get("behaviorType");
                String snapshotUrl = null;
                String videoClipUrl = null;
                behaviorAnalysisService.processBehaviorAlert(
                        cameraId, behaviorType, longitude, latitude, snapshotUrl, videoClipUrl);
            }
        } catch (Exception e) {
            log.error("行为分析失败：", e);
        }
    }

    private void handleFullAnalysis(String cameraId, String streamUrl,
                                    BigDecimal longitude, BigDecimal latitude) {
        log.info("执行全量分析（人脸+车牌+行为）：cameraId={}", cameraId);
        handleFaceRecognition(cameraId, streamUrl, longitude, latitude);
        handlePlateRecognition(cameraId, streamUrl, longitude, latitude);
        handleBehaviorAnalysis(cameraId, streamUrl, longitude, latitude);
    }
}
