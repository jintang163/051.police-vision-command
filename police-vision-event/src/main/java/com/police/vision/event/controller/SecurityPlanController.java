package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.SecurityPlanCreateDTO;
import com.police.vision.event.entity.SecSecurityPlan;
import com.police.vision.event.service.SecurityPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "安保方案管理", description = "安保方案创建、发布、执行、归档及查询接口")
@RestController
@RequestMapping("/event/plan")
@RequiredArgsConstructor
@Slf4j
public class SecurityPlanController {

    private final SecurityPlanService securityPlanService;

    @Operation(summary = "创建安保方案")
    @PostMapping("/create")
    public Result<SecSecurityPlan> createPlan(@RequestBody @Valid SecurityPlanCreateDTO dto) {
        return Result.success(securityPlanService.createPlan(dto));
    }

    @Operation(summary = "发布安保方案")
    @PostMapping("/publish/{planId}")
    public Result<Void> publishPlan(@PathVariable Long planId) {
        securityPlanService.publishPlan(planId);
        return Result.success();
    }

    @Operation(summary = "执行安保方案")
    @PostMapping("/execute/{planId}")
    public Result<Void> executePlan(@PathVariable Long planId) {
        securityPlanService.executePlan(planId);
        return Result.success();
    }

    @Operation(summary = "归档安保方案")
    @PostMapping("/archive/{planId}")
    public Result<Void> archivePlan(@PathVariable Long planId) {
        securityPlanService.archivePlan(planId);
        return Result.success();
    }

    @Operation(summary = "获取安保方案详情")
    @GetMapping("/{planId}")
    public Result<Map<String, Object>> getPlanDetail(@PathVariable Long planId) {
        return Result.success(securityPlanService.getPlanDetail(planId));
    }

    @Operation(summary = "分页查询安保方案列表")
    @GetMapping("/list")
    public Result<PageResult<SecSecurityPlan>> listPlans(
            @RequestParam Long eventId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(securityPlanService.listPlans(eventId, status, page, size));
    }

    @Operation(summary = "删除安保方案")
    @DeleteMapping("/{planId}")
    public Result<Void> deletePlan(@PathVariable Long planId) {
        securityPlanService.deletePlan(planId);
        return Result.success();
    }
}
