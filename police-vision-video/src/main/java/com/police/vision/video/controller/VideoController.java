package com.police.vision.video.controller;

import com.police.vision.common.result.Result;
import com.police.vision.video.service.VideoStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

@Tag(name = "视频存储", description = "视频上传、快照上传、文件下载、URL获取")
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoStorageService videoStorageService;

    @Operation(summary = "上传视频片段")
    @PostMapping("/upload")
    public Result<Map<String, String>> uploadVideo(
            @RequestParam("video") MultipartFile video,
            @RequestParam("cameraId") String cameraId) {
        String videoPath = videoStorageService.saveVideoClip(video, cameraId);
        Map<String, String> result = Map.of(
                "videoPath", videoPath,
                "videoUrl", videoStorageService.getVideoUrl(videoPath)
        );
        return Result.success(result);
    }

    @Operation(summary = "上传快照")
    @PostMapping("/snapshot")
    public Result<Map<String, String>> uploadSnapshot(
            @RequestParam("image") MultipartFile image,
            @RequestParam("cameraId") String cameraId) {
        String snapshotPath = videoStorageService.saveSnapshot(image, cameraId);
        Map<String, String> result = Map.of(
                "snapshotPath", snapshotPath,
                "snapshotUrl", videoStorageService.getSnapshotUrl(snapshotPath)
        );
        return Result.success(result);
    }

    @Operation(summary = "同时上传视频和快照")
    @PostMapping("/upload/both")
    public Result<Map<String, String>> uploadVideoAndSnapshot(
            @RequestParam("video") MultipartFile video,
            @RequestParam("snapshot") MultipartFile snapshot,
            @RequestParam("cameraId") String cameraId) {
        return Result.success(videoStorageService.uploadVideoWithSnapshot(video, snapshot, cameraId));
    }

    @Operation(summary = "获取视频预签名URL")
    @GetMapping("/url/{fileName}")
    public Result<String> getVideoUrl(@PathVariable String fileName) {
        return Result.success(videoStorageService.getVideoUrl(fileName));
    }

    @Operation(summary = "获取快照预签名URL")
    @GetMapping("/snapshot/url/{fileName}")
    public Result<String> getSnapshotUrl(@PathVariable String fileName) {
        return Result.success(videoStorageService.getSnapshotUrl(fileName));
    }

    @Operation(summary = "下载视频")
    @GetMapping("/download/{fileName}")
    public ResponseEntity<InputStreamResource> downloadVideo(@PathVariable String fileName) {
        InputStream inputStream = videoStorageService.downloadVideo(fileName);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(inputStream));
    }

    @Operation(summary = "下载快照")
    @GetMapping("/snapshot/download/{fileName}")
    public ResponseEntity<InputStreamResource> downloadSnapshot(@PathVariable String fileName) {
        InputStream inputStream = videoStorageService.downloadSnapshot(fileName);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE);
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(inputStream));
    }

    @Operation(summary = "删除视频")
    @DeleteMapping("/{fileName}")
    public Result<Void> deleteVideo(@PathVariable String fileName) {
        videoStorageService.deleteVideo(fileName);
        return Result.success();
    }

    @Operation(summary = "删除快照")
    @DeleteMapping("/snapshot/{fileName}")
    public Result<Void> deleteSnapshot(@PathVariable String fileName) {
        videoStorageService.deleteSnapshot(fileName);
        return Result.success();
    }

    @Operation(summary = "检查视频是否存在")
    @GetMapping("/exists/{fileName}")
    public Result<Boolean> checkVideoExists(@PathVariable String fileName) {
        return Result.success(videoStorageService.checkVideoExists(fileName));
    }

    @Operation(summary = "检查快照是否存在")
    @GetMapping("/snapshot/exists/{fileName}")
    public Result<Boolean> checkSnapshotExists(@PathVariable String fileName) {
        return Result.success(videoStorageService.checkSnapshotExists(fileName));
    }
}
