package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.EventCreateDTO;
import com.police.vision.event.dto.EventUpdateDTO;
import com.police.vision.event.entity.SecEvent;
import com.police.vision.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "活动管理", description = "活动创建、更新、删除、查询及启停管理接口")
@RestController
@RequestMapping("/event")
@RequiredArgsConstructor
@Slf4j
public class EventController {

    private final EventService eventService;

    @Operation(summary = "创建活动")
    @PostMapping("/create")
    public Result<Long> createEvent(@RequestBody @Valid EventCreateDTO dto) {
        return eventService.createEvent(dto);
    }

    @Operation(summary = "更新活动")
    @PutMapping("/update")
    public Result<Void> updateEvent(@RequestBody @Valid EventUpdateDTO dto) {
        return eventService.updateEvent(dto);
    }

    @Operation(summary = "删除活动")
    @DeleteMapping("/{id}")
    public Result<Void> deleteEvent(@PathVariable Long id) {
        return eventService.deleteEvent(id);
    }

    @Operation(summary = "根据ID查询活动详情")
    @GetMapping("/{id}")
    public Result<SecEvent> getEventById(@PathVariable Long id) {
        return eventService.getEventById(id);
    }

    @Operation(summary = "分页查询活动列表")
    @GetMapping("/list")
    public Result<PageResult<SecEvent>> listEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return eventService.listEvents(keyword, status, page, size);
    }

    @Operation(summary = "启动活动")
    @PostMapping("/start/{id}")
    public Result<Void> startEvent(@PathVariable Long id) {
        return eventService.startEvent(id);
    }

    @Operation(summary = "结束活动")
    @PostMapping("/end/{id}")
    public Result<Void> endEvent(@PathVariable Long id) {
        return eventService.endEvent(id);
    }
}
