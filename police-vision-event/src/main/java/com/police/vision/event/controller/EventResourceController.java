package com.police.vision.event.controller;

import com.police.vision.common.result.Result;
import com.police.vision.event.dto.AreaQueryDTO;
import com.police.vision.event.entity.SecEventResource;
import com.police.vision.event.service.EventResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "活动资源管理", description = "活动资源分配、警力和摄像头资源查询接口")
@RestController
@RequestMapping("/event/resource")
@RequiredArgsConstructor
@Slf4j
public class EventResourceController {

    private final EventResourceService eventResourceService;

    @Operation(summary = "分配活动区域资源")
    @PostMapping("/allocate")
    public Result<Integer> allocateResources(@RequestBody @Valid AreaQueryDTO dto) {
        return eventResourceService.allocateResources(dto.getEventId(), dto.getRadius() != null ? dto.getRadius().doubleValue() : null);
    }

    @Operation(summary = "查询活动警力资源列表")
    @GetMapping("/police/{eventId}")
    public Result<List<SecEventResource>> listPoliceResources(@PathVariable Long eventId) {
        return eventResourceService.listPoliceResources(eventId);
    }

    @Operation(summary = "查询活动摄像头资源列表")
    @GetMapping("/camera/{eventId}")
    public Result<List<SecEventResource>> listCameraResources(@PathVariable Long eventId) {
        return eventResourceService.listCameraResources(eventId);
    }
}
