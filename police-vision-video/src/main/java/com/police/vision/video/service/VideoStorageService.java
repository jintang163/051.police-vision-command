package com.police.vision.video.service;

import com.police.vision.common.config.MinioConfig;
import com.police.vision.common.util.MinioUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoStorageService {

    private final MinioUtil minioUtil;
    private final MinioConfig minioConfig;

    private static final String VIDEO_BUCKET = "video-clips";
    private static final String SNAPSHOT_BUCKET = "snapshots";
    private static final String VIDEO_CONTENT_TYPE = "video/mp4";
    private static final String IMAGE_CONTENT_TYPE = "image/jpeg";

    public String saveVideoClip(InputStream video, String cameraId,
                                LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String fileName = generateVideoPath(cameraId, startTime, endTime);
            String savedName = minioUtil.uploadFile(video, fileName, VIDEO_CONTENT_TYPE, VIDEO_BUCKET);
            log.info("保存视频片段成功：cameraId={}, fileName={}", cameraId, savedName);
            return savedName;
        } catch (Exception e) {
            log.error("保存视频片段失败：", e);
            throw new RuntimeException("保存视频片段失败", e);
        }
    }

    public String saveVideoClip(MultipartFile video, String cameraId) {
        try {
            String fileName = generateVideoPath(cameraId, LocalDateTime.now().minusSeconds(10), LocalDateTime.now());
            String savedName = minioUtil.uploadFile(video.getInputStream(), fileName,
                    video.getContentType() != null ? video.getContentType() : VIDEO_CONTENT_TYPE, VIDEO_BUCKET);
            log.info("上传视频片段成功：cameraId={}, fileName={}", cameraId, savedName);
            return savedName;
        } catch (Exception e) {
            log.error("上传视频片段失败：", e);
            throw new RuntimeException("上传视频片段失败", e);
        }
    }

    public String saveSnapshot(InputStream image, String cameraId) {
        try {
            String fileName = generateImagePath(cameraId);
            String savedName = minioUtil.uploadFile(image, fileName, IMAGE_CONTENT_TYPE, SNAPSHOT_BUCKET);
            log.info("保存快照成功：cameraId={}, fileName={}", cameraId, savedName);
            return savedName;
        } catch (Exception e) {
            log.error("保存快照失败：", e);
            throw new RuntimeException("保存快照失败", e);
        }
    }

    public String saveSnapshot(MultipartFile image, String cameraId) {
        try {
            String fileName = generateImagePath(cameraId);
            String savedName = minioUtil.uploadFile(image.getInputStream(), fileName,
                    image.getContentType() != null ? image.getContentType() : IMAGE_CONTENT_TYPE, SNAPSHOT_BUCKET);
            log.info("上传快照成功：cameraId={}, fileName={}", cameraId, savedName);
            return savedName;
        } catch (Exception e) {
            log.error("上传快照失败：", e);
            throw new RuntimeException("上传快照失败", e);
        }
    }

    public String getVideoUrl(String fileName) {
        try {
            String url = minioUtil.getPresignedUrl(fileName, VIDEO_BUCKET);
            log.debug("获取视频URL：fileName={}, url={}", fileName, url);
            return url;
        } catch (Exception e) {
            log.error("获取视频URL失败：", e);
            throw new RuntimeException("获取视频URL失败", e);
        }
    }

    public String getSnapshotUrl(String fileName) {
        try {
            String url = minioUtil.getPresignedUrl(fileName, SNAPSHOT_BUCKET);
            log.debug("获取快照URL：fileName={}, url={}", fileName, url);
            return url;
        } catch (Exception e) {
            log.error("获取快照URL失败：", e);
            throw new RuntimeException("获取快照URL失败", e);
        }
    }

    public Map<String, String> uploadVideoWithSnapshot(MultipartFile video, MultipartFile snapshot, String cameraId) {
        Map<String, String> result = new HashMap<>();
        String videoPath = saveVideoClip(video, cameraId);
        String snapshotPath = saveSnapshot(snapshot, cameraId);
        result.put("videoPath", videoPath);
        result.put("videoUrl", getVideoUrl(videoPath));
        result.put("snapshotPath", snapshotPath);
        result.put("snapshotUrl", getSnapshotUrl(snapshotPath));
        return result;
    }

    public InputStream downloadVideo(String fileName) {
        try {
            return minioUtil.downloadFile(fileName, VIDEO_BUCKET);
        } catch (Exception e) {
            log.error("下载视频失败：", e);
            throw new RuntimeException("下载视频失败", e);
        }
    }

    public InputStream downloadSnapshot(String fileName) {
        try {
            return minioUtil.downloadFile(fileName, SNAPSHOT_BUCKET);
        } catch (Exception e) {
            log.error("下载快照失败：", e);
            throw new RuntimeException("下载快照失败", e);
        }
    }

    public void deleteVideo(String fileName) {
        try {
            minioUtil.deleteFile(fileName, VIDEO_BUCKET);
            log.info("删除视频成功：fileName={}", fileName);
        } catch (Exception e) {
            log.error("删除视频失败：", e);
        }
    }

    public void deleteSnapshot(String fileName) {
        try {
            minioUtil.deleteFile(fileName, SNAPSHOT_BUCKET);
            log.info("删除快照成功：fileName={}", fileName);
        } catch (Exception e) {
            log.error("删除快照失败：", e);
        }
    }

    public boolean checkVideoExists(String fileName) {
        return minioUtil.checkFileExists(fileName, VIDEO_BUCKET);
    }

    public boolean checkSnapshotExists(String fileName) {
        return minioUtil.checkFileExists(fileName, SNAPSHOT_BUCKET);
    }

    private String generateVideoPath(String cameraId, LocalDateTime startTime, LocalDateTime endTime) {
        String dateStr = startTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
        String timeStr = startTime.format(DateTimeFormatter.ofPattern("HHmmss"));
        String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("HHmmss"));
        return cameraId + "/" + dateStr + "/" + cameraId + "_" + timeStr + "_" + endTimeStr + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".mp4";
    }

    private String generateImagePath(String cameraId) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        return cameraId + "/" + dateStr + "/" + cameraId + "_" + timeStr + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".jpg";
    }

    public String getDefaultBucket() {
        return minioConfig.getBucketName();
    }
}
