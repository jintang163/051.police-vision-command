package com.police.vision.video.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.enums.AlertTypeEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.video.client.ArcFaceClient;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.entity.FaceRecord;
import com.police.vision.video.entity.TargetPerson;
import com.police.vision.video.entity.VideoStorage;
import com.police.vision.video.mapper.CameraDeviceMapper;
import com.police.vision.video.mapper.FaceRecordMapper;
import com.police.vision.video.mapper.TargetPersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceRecognitionService {

    private final FaceRecordMapper faceRecordMapper;
    private final TargetPersonMapper targetPersonMapper;
    private final CameraDeviceMapper cameraDeviceMapper;
    private final ElasticsearchClient elasticsearchClient;
    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;
    private final ArcFaceClient arcFaceClient;
    private final VideoClipService videoClipService;

    private static final String FACE_INDEX = "target_person_faces";
    private static final float DEFAULT_THRESHOLD = 0.8f;

    public float[] extractFaceFeature(byte[] image) {
        try {
            log.info("调用ArcFace提取人脸特征，图片大小：{} bytes", image.length);
            return arcFaceClient.extractFeature(image);
        } catch (Exception e) {
            log.error("提取人脸特征失败：", e);
            throw new RuntimeException("人脸特征提取失败", e);
        }
    }

    public Map<String, Object> detectAndExtractFace(byte[] image) {
        try {
            log.info("人脸检测与特征提取，图片大小：{} bytes", image.length);
            float[] feature = extractFaceFeature(image);

            Map<String, Object> result = new HashMap<>();
            result.put("detected", feature != null && feature.length > 0);
            result.put("feature", feature);

            if (feature != null && feature.length > 0) {
                List<Map<String, Object>> searchResults = searchFaceByFeature(feature, DEFAULT_THRESHOLD);
                result.put("matches", searchResults);
                if (!searchResults.isEmpty()) {
                    Map<String, Object> bestMatch = searchResults.get(0);
                    result.put("bestMatch", bestMatch);
                    result.put("similarity", bestMatch.get("similarity"));
                }
            }

            return result;
        } catch (Exception e) {
            log.error("人脸检测失败：", e);
            Map<String, Object> result = new HashMap<>();
            result.put("detected", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    public List<Map<String, Object>> searchFaceByFeature(float[] feature, float threshold) {
        try {
            if (threshold <= 0) {
                threshold = DEFAULT_THRESHOLD;
            }
            Map<String, Object> scriptParams = new HashMap<>();
            scriptParams.put("query_vector", feature);
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(FACE_INDEX)
                            .query(q -> q
                                    .scriptScore(ss -> ss
                                            .query(mq -> mq.matchAll(ma -> ma))
                                            .script(sc -> sc
                                                    .inline(i -> i
                                                            .source("cosineSimilarity(params.query_vector, 'face_feature') + 1.0")
                                                            .params(scriptParams)
                                                    )
                                            )
                                            .minScore(threshold)
                                    )
                            )
                            .size(10),
                    Map.class
            );
            List<Map<String, Object>> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null && hit.score() != null) {
                    Map<String, Object> result = new HashMap<>(hit.source());
                    result.put("similarity", (hit.score() - 1) / 2);
                    results.add(result);
                }
            }
            log.info("人脸特征检索完成，找到 {} 个匹配结果", results.size());
            return results;
        } catch (Exception e) {
            log.error("人脸特征检索失败：", e);
            return Collections.emptyList();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addFaceToTarget(TargetPerson person) {
        TargetPerson exist = targetPersonMapper.selectByIdCardNo(person.getIdCardNo());
        if (exist != null) {
            person.setId(exist.getId());
            targetPersonMapper.updateById(person);
        } else {
            person.setId(SnowflakeIdUtil.nextId());
            person.setPersonId("P" + SnowflakeIdUtil.nextId());
            targetPersonMapper.insert(person);
        }
        if (person.getFaceFeature() != null) {
            try {
                Map<String, Object> doc = new HashMap<>();
                doc.put("person_id", person.getPersonId());
                doc.put("person_name", person.getPersonName());
                doc.put("id_card_no", person.getIdCardNo());
                doc.put("face_feature", JSON.parseObject(person.getFaceFeature(), float[].class));
                doc.put("control_level", person.getControlLevel());
                elasticsearchClient.index(i -> i
                        .index(FACE_INDEX)
                        .id(person.getPersonId())
                        .document(doc)
                );
            } catch (Exception e) {
                log.error("同步人脸特征到Elasticsearch失败：", e);
            }
        }
        redisUtil.delete(RedisConstant.TARGET_PERSON_KEY + "*");
        log.info("添加重点人员布控成功：personId={}", person.getPersonId());
    }

    public TargetPerson getTargetPerson(String personId) {
        return targetPersonMapper.selectByPersonId(personId);
    }

    public List<TargetPerson> getTargetPersonByStatus(Integer status) {
        String key = RedisConstant.TARGET_PERSON_KEY + "status:" + status;
        List<TargetPerson> cache = redisUtil.getObject(key, List.class);
        if (cache != null) {
            return cache;
        }
        List<TargetPerson> list = targetPersonMapper.selectByStatus(status);
        redisUtil.setObject(key, list, 1, TimeUnit.HOURS);
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleFaceMatch(FaceRecord record) {
        record.setId(SnowflakeIdUtil.nextId());
        record.setRecordId("FR" + SnowflakeIdUtil.nextId());
        if (record.getDetectTime() == null) {
            record.setDetectTime(LocalDateTime.now());
        }
        faceRecordMapper.insert(record);
        if (record.getPersonId() != null) {
            TargetPerson target = targetPersonMapper.selectByPersonId(record.getPersonId());
            if (target != null && target.getStatus() == 1) {
                CameraDevice camera = cameraDeviceMapper.selectByDeviceId(record.getCameraId());

                VideoStorage videoClip = null;
                try {
                    videoClip = videoClipService.captureAlertVideo(
                            record.getCameraId(),
                            record.getDetectTime()
                    );
                    log.info("人脸匹配告警视频截取完成：storageId={}", videoClip.getStorageId());
                } catch (Exception e) {
                    log.error("截取人脸匹配告警视频失败：{}", e.getMessage());
                }

                AlertMessageDTO alert = new AlertMessageDTO();
                alert.setAlertId("A" + SnowflakeIdUtil.nextId());
                alert.setAlertType(AlertTypeEnum.FACE_MATCH.getCode());
                alert.setAlertName(AlertTypeEnum.FACE_MATCH.getName());
                alert.setAlertLevel(target.getControlLevel());
                alert.setCameraId(record.getCameraId());
                alert.setCameraName(camera != null ? camera.getDeviceName() : "");
                alert.setLongitude(record.getLongitude());
                alert.setLatitude(record.getLatitude());
                alert.setDescription("重点人员人脸识别告警：" + record.getPersonName() +
                        "，相似度：" + record.getSimilarity() + "%");
                alert.setSnapshotUrl(record.getSnapshotUrl());
                alert.setAlertTime(record.getDetectTime());
                alert.setTargetPersonId(record.getPersonId());
                alert.setTargetPersonName(record.getPersonName());
                if (videoClip != null) {
                    try {
                        alert.setVideoClipUrl(videoClipService.getVideoUrl(videoClip.getFilePath()));
                    } catch (Exception e) {
                        log.warn("获取告警视频URL失败：{}", e.getMessage());
                    }
                }
                Map<String, Object> extra = new HashMap<>();
                extra.put("similarity", record.getSimilarity());
                extra.put("idCardNo", target.getIdCardNo());
                extra.put("videoStorageId", videoClip != null ? videoClip.getStorageId() : null);
                alert.setExtraData(extra);
                mqUtil.sendVideoAlert(alert);

                Map<String, Object> controlMsg = new HashMap<>();
                controlMsg.put("personId", record.getPersonId());
                controlMsg.put("personName", record.getPersonName());
                controlMsg.put("cameraId", record.getCameraId());
                controlMsg.put("cameraName", camera != null ? camera.getDeviceName() : "");
                controlMsg.put("longitude", record.getLongitude());
                controlMsg.put("latitude", record.getLatitude());
                controlMsg.put("snapshotUrl", record.getSnapshotUrl());
                controlMsg.put("videoClipUrl", videoClip != null ? videoClipService.getVideoUrl(videoClip.getFilePath()) : null);
                controlMsg.put("similarity", record.getSimilarity());
                controlMsg.put("detectTime", record.getDetectTime() != null ? record.getDetectTime().toString() : LocalDateTime.now().toString());
                mqUtil.send(MqConstant.CONTROL_TOPIC + ":face_match", controlMsg);

                log.info("人脸匹配告警已发送：personId={}, similarity={}, videoStorageId={}",
                        record.getPersonId(), record.getSimilarity(),
                        videoClip != null ? videoClip.getStorageId() : null);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveFaceRecord(FaceRecord record) {
        record.setId(SnowflakeIdUtil.nextId());
        record.setRecordId("FR" + SnowflakeIdUtil.nextId());
        if (record.getDetectTime() == null) {
            record.setDetectTime(LocalDateTime.now());
        }
        faceRecordMapper.insert(record);
    }
}
