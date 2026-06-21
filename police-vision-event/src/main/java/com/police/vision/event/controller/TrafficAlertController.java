package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.TrafficMonitorDTO;
import com.police.vision.event.entity.SecTrafficAlert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "交通预警管理", description = "交通预警查询、处理、统计及监控启动接口")
@RestController
@RequestMapping("/event/alert")
@RequiredArgsConstructor
@Slf4j
public class TrafficAlertController {

    private final com.police.vision.event.service.TrafficAlertService trafficAlertService;

    @Operation(summary = "分页查询交通预警列表")
    @GetMapping("/list")
    public Result<PageResult<SecTrafficAlert>> listAlerts(
            @RequestParam Long eventId,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) Integer handled,
            @RequestParam(required = false) Integer alertLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return trafficAlertService.listAlerts(eventId, alertType, handled, alertLevel, page, size);
    }

    @Operation(summary = "处理交通预警")
    @PostMapping("/handle/{alertId}")
    public Result<Void> handleAlert(@PathVariable Long alertId, @RequestBody Map<String, String> body) {
        String handleRemark = body.get("handleRemark");
        return trafficAlertService.handleAlert(alertId, handleRemark);
    }

    @Operation(summary = "获取交通预警统计数据")
    @GetMapping("/stats/{eventId}")
    public Result<Map<String, Object>> getAlertStats(@PathVariable Long eventId) {
        return trafficAlertService.getAlertStats(eventId);
    }

    @Operation(summary = "启动交通监控任务（异步提交Flink任务）")
    @PostMapping("/monitor/start")
    public Result<String> startTrafficMonitor(@RequestBody @Valid TrafficMonitorDTO dto) {
        return trafficAlertService.startTrafficMonitor(dto);
    }

    @Operation(summary = "停止交通监控任务")
    @PostMapping("/monitor/stop/{eventId}")
    public Result<Void> stopTrafficMonitor(@PathVariable Long eventId) {
        return trafficAlertService.stopTrafficMonitor(eventId);
    }

    @Operation(summary = "获取交通监控状态")
    @GetMapping("/monitor/status/{eventId}")
    public Result<Map<String, Object>> getMonitorStatus(@PathVariable Long eventId) {
        return trafficAlertService.getMonitorStatus(eventId);
    }
}
