package com.police.vision.video.controller;

import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.video.entity.AlertRecord;
import com.police.vision.video.service.AlertService;
import com.police.vision.video.service.BehaviorAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "告警管理", description = "告警查询、处理、统计")
@RestController
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final BehaviorAnalysisService behaviorAnalysisService;

    @Operation(summary = "分页查询告警列表")
    @GetMapping("/list")
    public Result<PageResult<AlertRecord>> getAlertList(
            PageParam param,
            @Parameter(description = "告警类型") @RequestParam(required = false) Integer alertType,
            @Parameter(description = "告警级别") @RequestParam(required = false) Integer alertLevel,
            @Parameter(description = "处理状态：0未处理 1已处理") @RequestParam(required = false) Integer processed,
            @Parameter(description = "摄像头ID") @RequestParam(required = false) String cameraId,
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.getAlertList(
                param, alertType, alertLevel, processed, cameraId, startTime, endTime));
    }

    @Operation(summary = "获取告警详情")
    @GetMapping("/{id}")
    public Result<AlertRecord> getAlertById(@PathVariable Long id) {
        return Result.success(alertService.getById(id));
    }

    @Operation(summary = "根据告警ID查询")
    @GetMapping("/alertId/{alertId}")
    public Result<AlertRecord> getAlertByAlertId(@PathVariable String alertId) {
        return Result.success(alertService.getByAlertId(alertId));
    }

    @Operation(summary = "获取摄像头的告警记录")
    @GetMapping("/camera/{cameraId}")
    public Result<List<AlertRecord>> getAlertsByCameraId(
            @PathVariable String cameraId,
            @Parameter(description = "返回数量限制") @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.success(alertService.getByCameraId(cameraId, limit));
    }

    @Operation(summary = "处理告警")
    @PutMapping("/{alertId}/process")
    public Result<Void> processAlert(
            @PathVariable Long alertId,
            @Parameter(description = "处理人ID") @RequestParam Long userId,
            @Parameter(description = "处理结果") @RequestParam String result) {
        alertService.processAlert(alertId, userId, result);
        return Result.success();
    }

    @Operation(summary = "告警统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getAlertStats(
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.getAlertStats(startTime, endTime));
    }

    @Operation(summary = "实时告警统计")
    @GetMapping("/stats/realtime")
    public Result<Map<String, Object>> getRealtimeStats() {
        return Result.success(alertService.getRealtimeStats());
    }

    @Operation(summary = "行为分析")
    @PostMapping("/behavior/analyze")
    public Result<Map<String, Object>> analyzeBehavior(
            @Parameter(description = "视频流地址") @RequestParam String streamUrl,
            @Parameter(description = "摄像头ID") @RequestParam String cameraId) {
        return Result.success(behaviorAnalysisService.analyzeBehavior(streamUrl, cameraId));
    }

    @Operation(summary = "发送行为分析告警")
    @PostMapping("/behavior/alert")
    public Result<Void> sendBehaviorAlert(
            @Parameter(description = "摄像头ID") @RequestParam String cameraId,
            @Parameter(description = "行为类型") @RequestParam Integer behaviorType,
            @Parameter(description = "经度") @RequestParam(required = false) BigDecimal longitude,
            @Parameter(description = "纬度") @RequestParam(required = false) BigDecimal latitude,
            @Parameter(description = "快照URL") @RequestParam(required = false) String snapshotUrl,
            @Parameter(description = "视频片段URL") @RequestParam(required = false) String videoClipUrl) {
        behaviorAnalysisService.processBehaviorAlert(
                cameraId, behaviorType, longitude, latitude, snapshotUrl, videoClipUrl);
        return Result.success();
    }
}
