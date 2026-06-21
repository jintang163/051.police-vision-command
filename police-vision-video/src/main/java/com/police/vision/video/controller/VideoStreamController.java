package com.police.vision.video.controller;

import com.police.vision.common.result.Result;
import com.police.vision.video.service.VideoClipService;
import com.police.vision.video.service.VideoStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Tag(name = "视频流管理", description = "摄像头直播流、快照、视频截取")
@RestController
@RequestMapping("/api/video/stream")
@RequiredArgsConstructor
public class VideoStreamController {

    private final VideoStreamService videoStreamService;
    private final VideoClipService videoClipService;

    @Operation(summary = "获取HTTP-FLV直播流")
    @GetMapping(value = "/flv/{cameraId}", produces = "video/x-flv")
    public void getFlvStream(@PathVariable String cameraId, HttpServletResponse response) throws Exception {
        videoStreamService.startHttpFlvStream(cameraId, response);
    }

    @Operation(summary = "获取摄像头快照")
    @GetMapping(value = "/snapshot/{cameraId}", produces = MediaType.IMAGE_JPEG_VALUE)
    public void getSnapshot(@PathVariable String cameraId, HttpServletResponse response) throws Exception {
        byte[] image = videoStreamService.getSnapshot(cameraId);
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        response.setContentLength(image.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(image);
            out.flush();
        }
    }

    @Operation(summary = "获取摄像头流信息")
    @GetMapping("/info/{cameraId}")
    public Result<Map<String, Object>> getStreamInfo(@PathVariable String cameraId) {
        return Result.success(videoStreamService.getStreamInfo(cameraId));
    }

    @Operation(summary = "手动截取告警视频")
    @PostMapping("/clip/{cameraId}")
    public Result<Map<String, Object>> captureAlertClip(
            @PathVariable String cameraId,
            @RequestParam(required = false) LocalDateTime alertTime,
            @RequestParam(defaultValue = "10") int beforeSeconds,
            @RequestParam(defaultValue = "10") int afterSeconds) {

        if (alertTime == null) {
            alertTime = LocalDateTime.now();
        }

        var storage = videoClipService.captureAlertVideo(cameraId, alertTime, beforeSeconds, afterSeconds);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("storageId", storage.getStorageId());
        result.put("fileName", storage.getFileName());
        result.put("fileSize", storage.getFileSize());
        result.put("duration", storage.getDuration());
        try {
            result.put("videoUrl", videoClipService.getVideoUrl(storage.getFilePath()));
            result.put("snapshotUrl", videoClipService.getSnapshotUrl(storage.getThumbnailUrl()));
        } catch (Exception e) {
            log.warn("获取视频URL失败：{}", e.getMessage());
        }
        return Result.success(result);
    }

    @Operation(summary = "停止所有视频流")
    @PostMapping("/stop-all")
    public Result<Void> stopAllStreams() {
        videoStreamService.stopAllStreams();
        return Result.success();
    }
}
