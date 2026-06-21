package com.police.vision.traffic.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.police.vision.common.entity.VehicleControl;
import com.police.vision.common.entity.VehicleControlAlert;
import com.police.vision.common.entity.VehicleTrackPoint;
import com.police.vision.common.result.Result;
import com.police.vision.traffic.service.VehicleControlService;
import com.police.vision.traffic.service.VehicleTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "车辆稽查布控", description = "车辆布控管理、告警查询、轨迹回放")
@RestController
@RequestMapping("/api/traffic")
@RequiredArgsConstructor
public class TrafficController {

    private final VehicleControlService vehicleControlService;
    private final VehicleTrackService vehicleTrackService;

    @Operation(summary = "新增车辆布控")
    @PostMapping("/control")
    public Result<Void> addVehicleControl(@RequestBody VehicleControl control) {
        vehicleControlService.addVehicleControl(control);
        return Result.success();
    }

    @Operation(summary = "更新车辆布控")
    @PutMapping("/control")
    public Result<Void> updateVehicleControl(@RequestBody VehicleControl control) {
        vehicleControlService.updateVehicleControl(control);
        return Result.success();
    }

    @Operation(summary = "撤销车辆布控")
    @DeleteMapping("/control/{id}")
    public Result<Void> deleteVehicleControl(@PathVariable Long id) {
        vehicleControlService.deleteVehicleControl(id);
        return Result.success();
    }

    @Operation(summary = "获取车辆布控详情")
    @GetMapping("/control/{id}")
    public Result<VehicleControl> getVehicleControl(@PathVariable Long id) {
        return Result.success(vehicleControlService.getVehicleControlById(id));
    }

    @Operation(summary = "分页查询车辆布控列表")
    @GetMapping("/control/list")
    public Result<IPage<VehicleControl>> getControlList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "车牌号") @RequestParam(required = false) String plateNo,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "布控类型") @RequestParam(required = false) Integer controlType) {
        return Result.success(vehicleControlService.getVehicleControlList(page, size, plateNo, status, controlType));
    }

    @Operation(summary = "分页查询车辆告警列表")
    @GetMapping("/alert/list")
    public Result<IPage<VehicleControlAlert>> getAlertList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "车牌号") @RequestParam(required = false) String plateNo,
            @Parameter(description = "告警类型") @RequestParam(required = false) Integer alertType,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        return Result.success(vehicleControlService.getAlertList(page, size, plateNo, alertType, status));
    }

    @Operation(summary = "处理告警")
    @PostMapping("/alert/{alertId}/handle")
    public Result<Void> handleAlert(
            @PathVariable Long alertId,
            @RequestParam String handleResult,
            @RequestParam(required = false) String handleRemark,
            @RequestParam Long handlerId,
            @RequestParam String handlerName) {
        vehicleControlService.handleAlert(alertId, handleResult, handleRemark, handlerId, handlerName);
        return Result.success();
    }

    @Operation(summary = "车辆轨迹回放")
    @GetMapping("/track/{plateNo}")
    public Result<List<VehicleTrackPoint>> getVehicleTrack(
            @PathVariable String plateNo,
            @Parameter(description = "开始时间") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(vehicleTrackService.getVehicleTrack(plateNo, startTime, endTime));
    }

    @Operation(summary = "搜索车辆")
    @GetMapping("/track/search")
    public Result<List<String>> searchVehicles(
            @Parameter(description = "车牌关键词") @RequestParam String keyword,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int limit) {
        return Result.success(vehicleTrackService.searchVehicles(keyword, limit));
    }
}
