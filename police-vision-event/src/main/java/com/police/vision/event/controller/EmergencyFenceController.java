package com.police.vision.event.controller;

import com.police.vision.common.result.Result;
import com.police.vision.event.dto.EmergencyFenceCreateDTO;
import com.police.vision.event.entity.SecEmergencyFence;
import com.police.vision.event.service.EmergencyFenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "图上作业-封控区管理", description = "指挥员在地图绘制封控区/管控区/防范区")
@RestController
@RequestMapping("/emergency/fence")
@RequiredArgsConstructor
public class EmergencyFenceController {

    private final EmergencyFenceService fenceService;

    @Operation(summary = "创建封控区", description = "绘制封控区多边形，支持封控/管控/防范/集结点/检查点5种类型")
    @PostMapping("/create")
    public Result<SecEmergencyFence> createFence(@RequestBody @Valid EmergencyFenceCreateDTO dto) {
        return Result.success(fenceService.createFence(dto));
    }

    @Operation(summary = "更新封控区")
    @PutMapping("/{fenceId}")
    public Result<Void> updateFence(@PathVariable Long fenceId,
                                     @RequestBody EmergencyFenceCreateDTO dto) {
        fenceService.updateFence(fenceId, dto);
        return Result.success();
    }

    @Operation(summary = "删除封控区")
    @DeleteMapping("/{fenceId}")
    public Result<Void> deleteFence(@PathVariable Long fenceId) {
        fenceService.deleteFence(fenceId);
        return Result.success();
    }

    @Operation(summary = "查询事件封控区列表")
    @GetMapping("/list")
    public Result<List<SecEmergencyFence>> listFences(
            @Parameter(description = "事件ID") @RequestParam Long eventId,
            @Parameter(description = "状态：1-有效，0-已删除") @RequestParam(required = false) Integer status) {
        return Result.success(fenceService.listFences(eventId, status));
    }

    @Operation(summary = "查询单个封控区详情")
    @GetMapping("/{fenceId}")
    public Result<SecEmergencyFence> getFenceById(@PathVariable Long fenceId) {
        return Result.success(fenceService.getFenceById(fenceId));
    }

    @Operation(summary = "批量删除事件下所有封控区")
    @DeleteMapping("/batch/event/{eventId}")
    public Result<Integer> batchDeleteFences(@PathVariable Long eventId) {
        return Result.success(fenceService.batchDeleteFences(eventId));
    }
}
