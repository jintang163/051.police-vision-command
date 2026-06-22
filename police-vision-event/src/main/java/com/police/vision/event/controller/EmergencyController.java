package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.EmergencyCommandCreateDTO;
import com.police.vision.event.dto.EmergencyCommandFeedbackDTO;
import com.police.vision.event.dto.EmergencyPlanStartDTO;
import com.police.vision.event.dto.EmergencyResourceQueryDTO;
import com.police.vision.event.entity.SecEmergencyCommand;
import com.police.vision.event.service.EmergencyDispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "应急指挥调度", description = "应急预案启动、指令下达、资源调度、闭环追踪")
@RestController
@RequestMapping("/emergency")
@RequiredArgsConstructor
@Slf4j
public class EmergencyController {

    private final EmergencyDispatchService dispatchService;

    @Operation(summary = "获取应急预案模板列表", description = "返回预设的暴恐、劫持、火灾等场景预案模板")
    @GetMapping("/plan/templates")
    public Result<List<Map<String, Object>>> getPlanTemplates() {
        return Result.success(dispatchService.listPlanTemplates());
    }

    @Operation(summary = "一键启动应急预案", description = "根据事件ID和模板代码启动应急预案，Sentinel限流熔断保护")
    @PostMapping("/plan/start")
    public Result<Map<String, Object>> startEmergencyPlan(@RequestBody @Valid EmergencyPlanStartDTO dto) {
        return Result.success(dispatchService.startEmergencyPlan(dto));
    }

    @Operation(summary = "下达应急指令", description = "Sentinel限流保护，通过RocketMQ广播至参战单位")
    @PostMapping("/command/dispatch")
    public Result<SecEmergencyCommand> dispatchCommand(@RequestBody @Valid EmergencyCommandCreateDTO dto) {
        return Result.success(dispatchService.dispatchCommand(dto));
    }

    @Operation(summary = "指令接收/执行/反馈/完成", description = "指令状态流转，全闭环追踪")
    @PostMapping("/command/feedback")
    public Result<SecEmergencyCommand> processCommandFeedback(@RequestBody @Valid EmergencyCommandFeedbackDTO dto) {
        return Result.success(dispatchService.processCommandFeedback(dto));
    }

    @Operation(summary = "分页查询应急指令列表")
    @GetMapping("/command/list")
    public Result<PageResult<SecEmergencyCommand>> listCommands(
            @Parameter(description = "事件ID") @RequestParam(required = false) Long eventId,
            @Parameter(description = "指令状态：0-已创建/1-已下达/2-已接收/3-执行中/4-已反馈/5-已完成/6-已取消/7-已超时")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "优先级：1-紧急/2-高/3-普通/4-低")
            @RequestParam(required = false) Integer priority,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        return Result.success(dispatchService.listCommands(eventId, status, priority, page, size));
    }

    @Operation(summary = "查询指令详情（含状态流转日志时间线）")
    @GetMapping("/command/{commandId}")
    public Result<Map<String, Object>> getCommandDetail(@PathVariable Long commandId) {
        return Result.success(dispatchService.getCommandDetail(commandId));
    }

    @Operation(summary = "查询事件周边应急资源", description = "按500米半径查询警力、摄像头、应急物资")
    @PostMapping("/resources/query")
    public Result<Map<String, Object>> queryEmergencyResources(@RequestBody @Valid EmergencyResourceQueryDTO dto) {
        return Result.success(dispatchService.queryEmergencyResources(dto));
    }
}
