package com.police.vision.alarm.controller;

import com.police.vision.alarm.entity.AlarmOrder;
import com.police.vision.alarm.entity.DispatchRecord;
import com.police.vision.alarm.service.AlarmOrderService;
import com.police.vision.alarm.service.DispatchService;
import com.police.vision.common.dto.AlarmCreateDTO;
import com.police.vision.common.dto.AlarmDispatchDTO;
import com.police.vision.common.dto.AlarmStatusUpdateDTO;
import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "警情管理", description = "警情接报派单管理接口")
@RestController
@RequestMapping("/alarm")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmOrderService alarmOrderService;
    private final DispatchService dispatchService;

    @Operation(summary = "创建警情工单")
    @PostMapping
    public Result<AlarmOrder> createAlarm(@Valid @RequestBody AlarmCreateDTO dto) {
        return Result.success(alarmOrderService.createAlarm(dto));
    }

    @Operation(summary = "分页查询警情列表")
    @GetMapping("/list")
    public Result<PageResult<AlarmOrder>> getAlarmList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNum(pageNum);
        pageParam.setPageSize(pageSize);
        return Result.success(alarmOrderService.getAlarmList(pageParam, status, type));
    }

    @Operation(summary = "获取警情详情")
    @GetMapping("/{id}")
    public Result<AlarmOrder> getAlarmDetail(@PathVariable Long id) {
        return Result.success(alarmOrderService.getAlarmDetail(id));
    }

    @Operation(summary = "更新警情状态")
    @PutMapping("/status")
    public Result<Void> updateAlarmStatus(@Valid @RequestBody AlarmStatusUpdateDTO dto) {
        alarmOrderService.updateAlarmStatus(dto);
        return Result.success();
    }

    @Operation(summary = "自动派单")
    @PostMapping("/auto-dispatch/{alarmId}")
    public Result<DispatchRecord> autoDispatch(@PathVariable Long alarmId) {
        return Result.success(dispatchService.autoDispatch(alarmId));
    }

    @Operation(summary = "人工派单")
    @PostMapping("/manual-dispatch")
    public Result<DispatchRecord> manualDispatch(@Valid @RequestBody AlarmDispatchDTO dto) {
        return Result.success(dispatchService.manualDispatch(dto));
    }

    @Operation(summary = "获取派单记录")
    @GetMapping("/dispatch/{alarmId}")
    public Result<DispatchRecord> getDispatchRecord(@PathVariable Long alarmId) {
        return Result.success(dispatchService.getDispatchRecord(alarmId));
    }

    @Operation(summary = "派单超时升级")
    @PostMapping("/escalate/{alarmId}/{commanderId}")
    public Result<Void> escalateAlarm(@PathVariable Long alarmId, @PathVariable Long commanderId) {
        dispatchService.escalateAlarm(alarmId, commanderId);
        return Result.success();
    }
}
