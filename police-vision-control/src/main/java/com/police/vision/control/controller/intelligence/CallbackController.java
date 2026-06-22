package com.police.vision.control.controller.intelligence;

import com.police.vision.common.result.Result;
import com.police.vision.control.dto.intelligence.*;
import com.police.vision.control.entity.intelligence.CallbackTemplate;
import com.police.vision.control.service.intelligence.CallbackDispatchService;
import com.police.vision.control.service.intelligence.CallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/intelligence/callback")
public class CallbackController {

    private final CallbackService callbackService;
    private final CallbackDispatchService dispatchService;

    @PostMapping("/task/create")
    public Result createTask(@RequestBody CallbackTaskCreateDTO dto) {
        return callbackService.createTask(dto);
    }

    @PostMapping("/task/page")
    public Result pageQuery(@RequestBody CallbackTaskQueryDTO dto) {
        return callbackService.pageQuery(dto);
    }

    @GetMapping("/task/{taskId}")
    public Result getTaskDetail(@PathVariable String taskId) {
        return callbackService.getTaskDetail(taskId);
    }

    @PostMapping("/task/manual-execute")
    public Result manualExecute(@RequestBody CallbackManualExecuteDTO dto) {
        return callbackService.manualExecute(dto);
    }

    @PostMapping("/task/retry/{taskId}")
    public Result retryTask(@PathVariable String taskId) {
        return callbackService.retryTask(taskId);
    }

    @PostMapping("/task/manual-transfer")
    public Result manualTransfer(@RequestBody Map<String, Object> body) {
        String taskId = body.get("taskId") != null ? body.get("taskId").toString() : null;
        Long officerId = body.get("officerId") != null ? Long.valueOf(body.get("officerId").toString()) : null;
        String officerName = body.get("officerName") != null ? body.get("officerName").toString() : null;
        return callbackService.manualTransfer(taskId, officerId, officerName);
    }

    @PostMapping("/task/manual-finish")
    public Result manualFinish(@RequestBody Map<String, Object> body) {
        String taskId = body.get("taskId") != null ? body.get("taskId").toString() : null;
        Long reviewerId = body.get("reviewerId") != null ? Long.valueOf(body.get("reviewerId").toString()) : null;
        String reviewerName = body.get("reviewerName") != null ? body.get("reviewerName").toString() : null;
        Integer timelinessScore = body.get("timelinessScore") != null ? Integer.valueOf(body.get("timelinessScore").toString()) : null;
        Integer attitudeScore = body.get("attitudeScore") != null ? Integer.valueOf(body.get("attitudeScore").toString()) : null;
        Integer solvingScore = body.get("solvingScore") != null ? Integer.valueOf(body.get("solvingScore").toString()) : null;
        Integer satisfactionLevel = body.get("satisfactionLevel") != null ? Integer.valueOf(body.get("satisfactionLevel").toString()) : null;
        String suggestionText = body.get("suggestionText") != null ? body.get("suggestionText").toString() : null;
        String summaryText = body.get("summaryText") != null ? body.get("summaryText").toString() : null;
        return callbackService.manualFinish(taskId, reviewerId, reviewerName,
                timelinessScore, attitudeScore, solvingScore, satisfactionLevel, suggestionText, summaryText);
    }

    @GetMapping("/template/list")
    public Result<List<CallbackTemplate>> listTemplates() {
        return callbackService.listTemplates();
    }

    @PostMapping("/statistics")
    public Result getStatistics(@RequestBody CallbackStatisticsDTO dto) {
        return callbackService.getStatistics(dto);
    }

    @PostMapping("/debug/scan/{limit}")
    public Result<Map<String, Object>> debugScan(@PathVariable Integer limit) {
        log.info("[警情回访-调试] 触发扫描任务, limit={}", limit);
        int count = dispatchService.scanAndDispatchTasks(limit);
        log.info("[警情回访-调试] 扫描任务完成, 共派发{}条", count);
        return Result.success(Map.of("dispatched", count));
    }

    @PostMapping("/debug/poll")
    public Result<Map<String, Object>> debugPoll() {
        log.info("[警情回访-调试] 触发轮询通话状态");
        int count = dispatchService.pollAndUpdateCallStatus();
        log.info("[警情回访-调试] 轮询完成, 更新{}条", count);
        return Result.success(Map.of("updated", count));
    }

    @PostMapping("/debug/retry/{limit}")
    public Result<Map<String, Object>> debugRetry(@PathVariable Integer limit) {
        log.info("[警情回访-调试] 触发重试失败任务, limit={}", limit);
        int count = dispatchService.retryFailedTasks(limit);
        log.info("[警情回访-调试] 重试完成, 共{}条", count);
        return Result.success(Map.of("retried", count));
    }
}
