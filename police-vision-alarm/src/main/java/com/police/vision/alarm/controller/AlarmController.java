package com.police.vision.alarm.controller;

import com.police.vision.alarm.entity.AlarmOrder;
import com.police.vision.alarm.entity.DispatchContext;
import com.police.vision.alarm.entity.DispatchRecord;
import com.police.vision.alarm.entity.PoliceOfficer;
import com.police.vision.alarm.mapper.DispatchTrafficSnapshotMapper;
import com.police.vision.alarm.service.*;
import com.police.vision.common.dto.*;
import com.police.vision.common.entity.DispatchTrafficSnapshot;
import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.common.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Tag(name = "警情管理", description = "警情接报派单管理接口（含智能派单V2）")
@RestController
@RequestMapping("/alarm")
@RequiredArgsConstructor
@Slf4j
public class AlarmController {

    private final AlarmOrderService alarmOrderService;
    private final DispatchService dispatchService;
    private final SmartDispatchService smartDispatchService;
    private final AmapRouteClientService amapRouteClientService;
    private final YawDetectionService yawDetectionService;
    private final RendezvousPlanningService rendezvousPlanningService;
    private final DispatchTrafficSnapshotMapper snapshotMapper;

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

    @Operation(summary = "自动派单（默认使用智能ETA算法）")
    @PostMapping("/auto-dispatch/{alarmId}")
    public Result<DispatchRecord> autoDispatch(
            @PathVariable Long alarmId,
            @Parameter(description = "是否启用智能ETA算法（默认true）")
            @RequestParam(required = false, defaultValue = "true") Boolean useSmartEta) {
        return Result.success(dispatchService.autoDispatch(alarmId, Boolean.TRUE.equals(useSmartEta)));
    }

    @Operation(summary = "人工派单（支持智能ETA辅助决策）")
    @PostMapping("/manual-dispatch")
    public Result<DispatchRecord> manualDispatch(@Valid @RequestBody AlarmDispatchDTO dto) {
        return Result.success(dispatchService.manualDispatch(dto));
    }

    @Operation(summary = "智能派单预览（不实际派单，仅计算推荐警力）")
    @PostMapping("/smart-preview")
    public Result<Map<String, Object>> smartDispatchPreview(
            @RequestParam Long alarmId,
            @RequestParam(required = false, defaultValue = "true") Boolean useSmartEta) {
        AlarmOrder alarm = alarmOrderService.getAlarmDetail(alarmId);

        DispatchContext context = new DispatchContext();
        context.setAlarmId(alarmId);
        context.setAlarmType(alarm.getAlarmType());
        context.setPriority(alarm.getPriority());
        context.setLongitude(alarm.getLongitude());
        context.setLatitude(alarm.getLatitude());
        context.setAlarmAddress(alarm.getAddress());
        context.setUseSmartEta(Boolean.TRUE.equals(useSmartEta));

        List<PoliceOfficer> officers = dispatchService.listAvailableOfficers();
        context.setAvailableOfficers(officers);

        DispatchContext result = Boolean.TRUE.equals(useSmartEta)
                ? smartDispatchService.calculateSmartDispatch(context)
                : null;

        Map<String, Object> response = new HashMap<>();
        response.put("alarmId", alarmId);
        response.put("useSmartEta", useSmartEta);
        response.put("recommendedOfficers", result != null ? result.getRecommendedOfficers() : Collections.emptyList());
        response.put("dispatchSuggestion", result != null ? result.getDispatchSuggestion() : "");
        response.put("officerEtaMap", result != null ? result.getOfficerEtaMap() : Collections.emptyMap());
        response.put("fastestEtaSeconds", result != null ? result.getFastestEtaSeconds() : null);
        response.put("fastestPoliceId", result != null ? result.getFastestPoliceId() : null);
        response.put("avgTrafficLevel", result != null ? result.getAvgTrafficLevel() : null);
        response.put("multiDispatchPlan", result != null ? result.getMultiDispatchPlan() : null);
        response.put("dispatchAlgorithm", result != null ? result.getDispatchAlgorithm() : "PREVIEW");
        return Result.success(response);
    }

    @Operation(summary = "派单重算（路况变化/偏航时使用）")
    @PostMapping("/recalc-dispatch/{alarmId}")
    public Result<DispatchRecord> recalcDispatch(
            @PathVariable Long alarmId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long commanderId) {
        Long finalCommanderId = commanderId != null ? commanderId : UserContext.getUserId();
        return Result.success(dispatchService.recalcDispatch(alarmId, finalCommanderId, reason));
    }

    @Operation(summary = "获取派单记录（最新一条）")
    @GetMapping("/dispatch/{alarmId}")
    public Result<DispatchRecord> getDispatchRecord(@PathVariable Long alarmId) {
        return Result.success(dispatchService.getDispatchRecord(alarmId));
    }

    @Operation(summary = "获取派单历史（所有派单记录）")
    @GetMapping("/dispatch-history/{alarmId}")
    public Result<List<DispatchRecord>> getDispatchHistory(@PathVariable Long alarmId) {
        return Result.success(dispatchService.getDispatchHistory(alarmId));
    }

    @Operation(summary = "派单超时升级")
    @PostMapping("/escalate/{alarmId}/{commanderId}")
    public Result<Void> escalateAlarm(@PathVariable Long alarmId, @PathVariable Long commanderId) {
        dispatchService.escalateAlarm(alarmId, commanderId);
        return Result.success();
    }

    @Operation(summary = "手动触发偏航检测")
    @PostMapping("/yaw-check")
    public Result<YawDetectionResultDTO> checkYaw(
            @RequestParam Long dispatchId,
            @RequestParam Long policeId,
            @RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude) {
        DispatchRecord dispatch = dispatchService.getDispatchRecordByDispatchId(dispatchId);
        String routePolyline = null;
        if (dispatch != null && dispatch.getTrafficSnapshotId() != null) {
            try {
                DispatchTrafficSnapshot snap = snapshotMapper.selectBySnapshotId(dispatch.getTrafficSnapshotId());
                if (snap != null && snap.getRoutePolylineData() != null) {
                    Map<String, String> routeMap = com.alibaba.fastjson2.JSON.parseObject(
                            snap.getRoutePolylineData(),
                            new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {});
                    routePolyline = routeMap.get(String.valueOf(policeId));
                }
            } catch (Exception ignored) {}
        }
        return Result.success(yawDetectionService.checkYaw(dispatchId, policeId, longitude, latitude, routePolyline));
    }

    @Operation(summary = "获取警情路况快照（派单时路况复盘用）")
    @GetMapping("/traffic-snapshot/{dispatchId}")
    public Result<List<DispatchTrafficSnapshot>> getTrafficSnapshot(@PathVariable Long dispatchId) {
        return Result.success(snapshotMapper.selectByDispatchId(dispatchId));
    }

    @Operation(summary = "查询路况快照列表")
    @GetMapping("/traffic-snapshot/list")
    public Result<List<DispatchTrafficSnapshot>> listTrafficSnapshot(
            @RequestParam(required = false) Long alarmId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        if (alarmId != null) {
            return Result.success(snapshotMapper.selectByAlarmId(alarmId));
        }
        if (startTime != null && endTime != null) {
            return Result.success(snapshotMapper.selectByTimeRange(startTime, endTime));
        }
        return Result.success(Collections.emptyList());
    }

    @Operation(summary = "获取会合点规划详情")
    @GetMapping("/rendezvous/{alarmId}")
    public Result<MultiDispatchPlanDTO> getRendezvousPlan(@PathVariable Long alarmId) {
        return Result.success(rendezvousPlanningService.getCachedRendezvousPlan(alarmId));
    }

    @Operation(summary = "查询指定区域实时路况")
    @GetMapping("/traffic-status")
    public Result<AmapTrafficStatusDTO> getTrafficStatus(
            @RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude,
            @RequestParam(defaultValue = "5000") Integer radius) {
        return Result.success(amapRouteClientService.getTrafficAround(longitude, latitude, radius != null ? radius : 5000));
    }

    @Operation(summary = "单独计算两点ETA（测试用）")
    @GetMapping("/calculate-eta")
    public Result<OfficerEtaResultDTO> calculateEta(
            @RequestParam Long policeId,
            @RequestParam String policeName,
            @RequestParam BigDecimal policeLon,
            @RequestParam BigDecimal policeLat,
            @RequestParam BigDecimal alarmLon,
            @RequestParam BigDecimal alarmLat) {
        return Result.success(amapRouteClientService.calculateOfficerEta(
                policeId, policeName, policeLon, policeLat, alarmLon, alarmLat));
    }
}
