package com.police.vision.gis.controller;

import com.police.vision.common.result.Result;
import com.police.vision.gis.entity.*;
import com.police.vision.gis.service.GisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "GIS地图服务", description = "警力分布、热力图、摄像头、巡逻车、图层管理")
@RestController
@RequestMapping("/gis")
@RequiredArgsConstructor
public class GisController {

    private final GisService gisService;

    @Operation(summary = "获取所有警力实时分布", description = "从Redis读取最新位置，Redis为空则从数据库查询")
    @GetMapping("/police/distribution")
    public Result<List<PoliceLocation>> getPoliceDistribution() {
        return Result.success(gisService.getPoliceDistribution());
    }

    @Operation(summary = "更新警员位置")
    @PostMapping("/police/location")
    public Result<Void> updatePoliceLocation(
            @Parameter(description = "警员ID") @RequestParam Long policeId,
            @Parameter(description = "经度") @RequestParam BigDecimal lng,
            @Parameter(description = "纬度") @RequestParam BigDecimal lat) {
        gisService.updatePoliceLocation(policeId, lng, lat);
        return Result.success();
    }

    @Operation(summary = "获取警情热力图数据")
    @GetMapping("/alarm/heatmap")
    public Result<List<AlarmHeatmap>> getAlarmHeatmap(
            @Parameter(description = "时间范围：today/week/month", required = false)
            @RequestParam(required = false, defaultValue = "today") String timeRange) {
        return Result.success(gisService.getAlarmHeatmap(timeRange));
    }

    @Operation(summary = "获取所有摄像头点位")
    @GetMapping("/camera/points")
    public Result<List<CameraPoint>> getCameraPoints() {
        return Result.success(gisService.getCameraPoints());
    }

    @Operation(summary = "获取所有巡逻车位置")
    @GetMapping("/patrol/cars")
    public Result<List<PatrolCar>> getPatrolCars() {
        return Result.success(gisService.getPatrolCars());
    }

    @Operation(summary = "查询指定范围内可用警力")
    @GetMapping("/police/nearby")
    public Result<List<PoliceLocation>> getNearbyPolice(
            @Parameter(description = "经度") @RequestParam BigDecimal lng,
            @Parameter(description = "纬度") @RequestParam BigDecimal lat,
            @Parameter(description = "搜索半径（公里）") @RequestParam(defaultValue = "1.0") double radiusKm) {
        return Result.success(gisService.getNearbyPolice(lng, lat, radiusKm));
    }

    @Operation(summary = "获取所有图层配置")
    @GetMapping("/layers")
    public Result<List<MapLayer>> getMapLayers() {
        return Result.success(gisService.getMapLayers());
    }

    @Operation(summary = "更新图层显示状态")
    @PutMapping("/layer/status")
    public Result<Void> updateLayerStatus(
            @Parameter(description = "图层编码") @RequestParam String layerCode,
            @Parameter(description = "是否可见：0-隐藏，1-显示") @RequestParam Integer visible) {
        gisService.updateLayerStatus(layerCode, visible);
        return Result.success();
    }
}
