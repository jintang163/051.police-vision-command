package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.RouteCreateDTO;
import com.police.vision.event.entity.SecRoute;
import com.police.vision.event.service.RouteSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "路线安保管理", description = "路线创建、查询、轮巡启动及摄像头查询接口")
@RestController
@RequestMapping("/route-security")
@RequiredArgsConstructor
@Slf4j
public class RouteSecurityController {

    private final RouteSecurityService routeSecurityService;

    @Operation(summary = "创建安保路线")
    @PostMapping("/create")
    public Result<SecRoute> createRoute(@RequestBody @Valid RouteCreateDTO dto) {
        return Result.success(routeSecurityService.createRoute(dto));
    }

    @Operation(summary = "获取路线详情")
    @GetMapping("/{routeId}")
    public Result<Map<String, Object>> getRouteDetail(@PathVariable Long routeId) {
        return Result.success(routeSecurityService.getRouteDetail(routeId));
    }

    @Operation(summary = "分页查询路线列表")
    @GetMapping("/list")
    public Result<PageResult<SecRoute>> listRoutes(
            @RequestParam Long eventId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(routeSecurityService.listRoutes(eventId, page, size));
    }

    @Operation(summary = "启动路线轮巡")
    @PostMapping("/patrol/start/{routeId}")
    public Result<Map<String, Object>> startRoutePatrol(@PathVariable Long routeId) {
        return Result.success(routeSecurityService.startRoutePatrol(routeId));
    }

    @Operation(summary = "获取路线轮巡摄像头列表")
    @GetMapping("/patrol/cameras/{routeId}")
    public Result<List<String>> getPatrolCameras(@PathVariable Long routeId) {
        return Result.success(routeSecurityService.getPatrolCameras(routeId));
    }
}
