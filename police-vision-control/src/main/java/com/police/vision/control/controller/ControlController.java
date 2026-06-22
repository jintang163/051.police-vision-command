package com.police.vision.control.controller;

import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.control.entity.*;
import com.police.vision.control.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/control")
@RequiredArgsConstructor
public class ControlController {

    private final TargetPersonProfileService profileService;
    private final GeoFenceService fenceService;
    private final TargetPersonAlertService alertService;
    private final com.police.vision.control.mapper.TargetPersonMapper targetPersonMapper;
    private final com.police.vision.control.mapper.AggregationAlertMapper aggregationAlertMapper;
    private final com.police.vision.control.service.PersonTrackService personTrackService;
    private final com.police.vision.control.service.TrajectoryPredictService trajectoryPredictService;
    private final com.police.vision.control.service.PredictionAlertService predictionAlertService;

    // ============================ 重点人员库 ============================

    @PostMapping("/person/save")
    public Result<TargetPerson> savePerson(@RequestBody TargetPerson person) {
        profileService.addOrUpdatePerson(person);
        return Result.success(person);
    }

    @GetMapping("/person/{personId}")
    public Result<TargetPerson> getPerson(@PathVariable String personId) {
        return Result.success(profileService.getPersonProfile(personId));
    }

    @GetMapping("/person/page")
    public Result<PageResult<TargetPerson>> pagePersons(
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) String personName,
            @RequestParam(required = false) String idCardNo,
            @RequestParam(required = false) Integer controlLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        LambdaQueryWrapper<TargetPerson> wrapper = new LambdaQueryWrapper<>();
        if (personType != null && !personType.isEmpty()) wrapper.eq(TargetPerson::getPersonType, personType);
        if (personName != null && !personName.isEmpty()) wrapper.like(TargetPerson::getPersonName, personName);
        if (idCardNo != null && !idCardNo.isEmpty()) wrapper.like(TargetPerson::getIdCardNo, idCardNo);
        if (controlLevel != null) wrapper.eq(TargetPerson::getControlLevel, controlLevel);
        if (status != null) wrapper.eq(TargetPerson::getStatus, status);
        wrapper.orderByDesc(TargetPerson::getAlertCount);

        IPage<TargetPerson> page = targetPersonMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/person/stats/summary")
    public Result<Map<String, Object>> getPersonStats() {
        Map<String, Object> stats = new HashMap<>();
        long total = targetPersonMapper.selectCount(null);
        stats.put("totalCount", total);

        String[] types = {"CRIMINAL", "APPEAL", "MENTAL", "DRUG", "TERROR", "OTHER"};
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String t : types) {
            LambdaQueryWrapper<TargetPerson> w = new LambdaQueryWrapper<>();
            w.eq(TargetPerson::getPersonType, t);
            typeCount.put(t, targetPersonMapper.selectCount(w));
        }
        stats.put("typeDistribution", typeCount);

        Map<String, Long> levelCount = new LinkedHashMap<>();
        for (int lv = 1; lv <= 4; lv++) {
            LambdaQueryWrapper<TargetPerson> w = new LambdaQueryWrapper<>();
            w.eq(TargetPerson::getControlLevel, lv);
            levelCount.put("L" + lv, targetPersonMapper.selectCount(w));
        }
        stats.put("levelDistribution", levelCount);

        LambdaQueryWrapper<TargetPerson> w = new LambdaQueryWrapper<>();
        w.eq(TargetPerson::getStatus, 1);
        stats.put("activeControlCount", targetPersonMapper.selectCount(w));

        stats.put("alertMetrics", alertService.getAlertMetrics());
        return Result.success(stats);
    }

    // ============================ 人员画像 ============================

    @GetMapping("/person/profile/{personId}")
    public Result<TargetPerson> getPersonProfile(@PathVariable String personId) {
        return Result.success(profileService.getPersonProfile(personId));
    }

    @GetMapping("/person/activity/{personId}")
    public Result<Map<String, Object>> getActivityStats(
            @PathVariable String personId,
            @RequestParam(defaultValue = "30") int days) {
        return Result.success(profileService.getActivityStats(personId, days));
    }

    // ============================ Neo4j关系图谱 ============================

    @GetMapping("/person/graph/{personId}")
    public Result<Map<String, Object>> getRelationGraph(
            @PathVariable String personId,
            @RequestParam(defaultValue = "2") int depth,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(profileService.getRelationGraph(personId, depth, limit));
    }

    @PostMapping("/relation/cocase")
    public Result<Void> addCoCaseRelation(
            @RequestParam String personId1,
            @RequestParam String personId2,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String caseName,
            @RequestParam(required = false) String description) {
        profileService.addCoCaseRelation(personId1, personId2, caseId, caseName, description);
        return Result.success();
    }

    @PostMapping("/relation/contact")
    public Result<Void> addFrequentContact(
            @RequestParam String personId1,
            @RequestParam String personId2,
            @RequestParam(defaultValue = "10") int contactCount,
            @RequestParam(defaultValue = "60") int strength) {
        profileService.addFrequentContact(personId1, personId2, contactCount, strength);
        return Result.success();
    }

    // ============================ 电子围栏 ============================

    @GetMapping("/fence/list")
    public Result<List<GeoFence>> listFences(
            @RequestParam(required = false) String fenceType,
            @RequestParam(required = false) String stationCode,
            @RequestParam(required = false) Boolean enabled) {
        return Result.success(fenceService.listFences(fenceType, stationCode, enabled));
    }

    @PostMapping("/fence/save")
    public Result<GeoFence> createOrUpdateFence(@RequestBody GeoFence fence) {
        if (fence.getId() == null) {
            return Result.success(fenceService.createFence(fence));
        }
        fenceService.updateFence(fence);
        return Result.success(fence);
    }

    @DeleteMapping("/fence/{fenceId}")
    public Result<Void> deleteFence(@PathVariable String fenceId) {
        fenceService.deleteFence(fenceId);
        return Result.success();
    }

    @PostMapping("/fence/check")
    public Result<Void> checkFence(
            @RequestParam String personId,
            @RequestParam String personName,
            @RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude,
            @RequestParam(required = false) String cameraId,
            @RequestParam(required = false) String cameraName,
            @RequestParam(required = false) String snapshotUrl) {
        fenceService.checkAndTriggerFenceAlert(personId, personName, longitude, latitude,
                cameraId, cameraName, snapshotUrl, null);
        return Result.success();
    }

    @PostMapping("/fence/leave")
    public Result<Void> leaveFence(
            @RequestParam String personId,
            @RequestParam String fenceId) {
        fenceService.markPersonLeaveFence(personId, fenceId);
        return Result.success();
    }

    // ============================ 围栏告警列表 ============================

    @GetMapping("/fence/alert/list")
    public Result<List<FenceAlert>> listFenceAlerts(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String personId,
            @RequestParam(required = false) String fenceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(fenceService.listAlerts(status, personId, fenceId, startTime, endTime));
    }

    @PostMapping("/fence/alert/{alertId}/handle")
    public Result<Void> handleFenceAlert(
            @PathVariable String alertId,
            @RequestParam(required = false) String statusName,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long officerId) {
        fenceService.handleAlert(alertId, statusName, remark, officerId);
        return Result.success();
    }

    // ============================ 聚集告警 ============================

    @GetMapping("/aggregation/list")
    public Result<List<AggregationAlert>> listAggregationAlerts(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        LambdaQueryWrapper<AggregationAlert> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(AggregationAlert::getStatus, status);
        if (areaCode != null && !areaCode.isEmpty()) wrapper.eq(AggregationAlert::getAreaCode, areaCode);
        if (startTime != null && endTime != null) {
            wrapper.between(AggregationAlert::getStartTime, startTime, endTime);
        }
        wrapper.orderByDesc(AggregationAlert::getStartTime);

        IPage<AggregationAlert> page = aggregationAlertMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return Result.success(page.getRecords());
    }

    @GetMapping("/aggregation/{alertId}")
    public Result<AggregationAlert> getAggregationAlert(@PathVariable String alertId) {
        LambdaQueryWrapper<AggregationAlert> w = new LambdaQueryWrapper<>();
        w.eq(AggregationAlert::getAlertId, alertId);
        return Result.success(aggregationAlertMapper.selectOne(w));
    }

    @PostMapping("/aggregation/{alertId}/handle")
    public Result<Void> handleAggregationAlert(
            @PathVariable String alertId,
            @RequestParam String statusName,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long officerId) {
        LambdaQueryWrapper<AggregationAlert> w = new LambdaQueryWrapper<>();
        w.eq(AggregationAlert::getAlertId, alertId);
        AggregationAlert alert = aggregationAlertMapper.selectOne(w);
        if (alert != null) {
            alert.setStatus(2);
            alert.setStatusName(statusName);
            alert.setHandleRemark(remark);
            alert.setHandleOfficerId(officerId);
            alert.setHandleTime(LocalDateTime.now());
            aggregationAlertMapper.updateById(alert);
        }
        return Result.success();
    }

    // ============================ 感知预警指标 ============================

    @GetMapping("/alert/metrics")
    public Result<Map<String, Object>> getAlertMetrics() {
        return Result.success(alertService.getAlertMetrics());
    }

    // ============================ 测试接口：模拟感知即预警 ============================

    @PostMapping("/test/face-match")
    public Result<Map<String, Object>> testFaceMatch(
            @RequestParam String personId,
            @RequestParam(required = false) String personName,
            @RequestParam(required = false) String cameraId,
            @RequestParam(required = false) String cameraName,
            @RequestParam(defaultValue = "116.397128") BigDecimal longitude,
            @RequestParam(defaultValue = "39.916527") BigDecimal latitude,
            @RequestParam(defaultValue = "95.5") Float similarity) {

        TargetPerson person = targetPersonMapper.selectByPersonId(personId);
        String name = personName != null ? personName : (person != null ? person.getPersonName() : "测试人员");
        String camName = cameraName != null ? cameraName : "测试摄像头";

        alertService.processFaceMatchInternal(personId, name, cameraId, camName,
                longitude, latitude, null, null, similarity);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("personId", personId);
        resp.put("personName", name);
        resp.put("similarity", similarity);
        resp.put("location", Map.of("lng", longitude, "lat", latitude));
        resp.put("timestamp", System.currentTimeMillis());
        return Result.success(resp);
    }

    // ============================ 轨迹历史 ============================

    @PostMapping("/track/add")
    public Result<Map<String, Object>> addTrackPoint(
            @RequestParam String personId,
            @RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude,
            @RequestParam(required = false) BigDecimal speed,
            @RequestParam(required = false) BigDecimal direction,
            @RequestParam(required = false, defaultValue = "GPS") String sourceType,
            @RequestParam(required = false) String deviceId) {
        return Result.success(Map.of(
                "added", personTrackService.addTrackPoint(personId, longitude, latitude,
                        speed, direction, sourceType, deviceId).size()
        ));
    }

    @GetMapping("/track/history/{personId}")
    public Result<List<?>> getTrackHistory(
            @PathVariable String personId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(personTrackService.getPersonTrackHistory(personId, startTime, endTime));
    }

    @GetMapping("/track/recent/{personId}")
    public Result<List<?>> getRecentTrack(
            @PathVariable String personId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "1000") int limit) {
        return Result.success(personTrackService.getRecentTrack(personId, days, limit));
    }

    @GetMapping("/track/activity-pattern/{personId}")
    public Result<Map<String, Object>> getActivityPattern(
            @PathVariable String personId,
            @RequestParam(defaultValue = "90") int days) {
        return Result.success(personTrackService.analyzeActivityPattern(personId, days));
    }

    // ============================ 轨迹预测（LSTM） ============================

    @PostMapping("/predict/trajectory/{personId}")
    public Result<Map<String, Object>> predictTrajectory(@PathVariable String personId) {
        return Result.success(trajectoryPredictService.predictTrajectory(personId));
    }

    @PostMapping("/predict/trajectory/batch")
    public Result<Map<String, Object>> predictBatch(@RequestBody List<String> personIds) {
        return Result.success(trajectoryPredictService.predictBatch(personIds));
    }

    @GetMapping("/predict/latest/{personId}")
    public Result<List<?>> getLatestPredictions(
            @PathVariable String personId,
            @RequestParam(defaultValue = "3") int limit) {
        return Result.success(trajectoryPredictService.getLatestPredictions(personId, limit));
    }

    @GetMapping("/predict/high-risk")
    public Result<List<?>> getHighRiskPredictions(
            @RequestParam(required = false) Double minProbability,
            @RequestParam(required = false) Integer sensitiveOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(trajectoryPredictService.getHighRiskPredictions(
                minProbability, sensitiveOnly, startTime, endTime));
    }

    // ============================ 预测预警 ============================

    @PostMapping("/predict-alert/generate/{predictionBatch}")
    public Result<List<?>> generateAlerts(@PathVariable String predictionBatch) {
        return Result.success(predictionAlertService.generateAlertsFromPredictions(predictionBatch));
    }

    @GetMapping("/predict-alert/page")
    public Result<Map<String, Object>> pagePredictionAlerts(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String personId,
            @RequestParam(required = false) Integer alertLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> result = new HashMap<>();
        result.put("list", predictionAlertService.listAlerts(
                status, personId, alertLevel, startTime, endTime, pageNum, pageSize));
        result.put("total", predictionAlertService.countAlerts(
                status, personId, alertLevel, startTime, endTime));
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return Result.success(result);
    }

    @PostMapping("/predict-alert/handle/{alertId}")
    public Result<Map<String, Object>> handlePredictionAlert(
            @PathVariable String alertId,
            @RequestParam(required = false) Integer targetStatus,
            @RequestParam(required = false) String statusName,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long officerId,
            @RequestParam(required = false) String officerName) {
        return Result.success(Map.of(
                "handled", predictionAlertService.handleAlert(
                        alertId, targetStatus, statusName, remark, officerId, officerName)
        ));
    }

    @PostMapping("/predict-alert/auto-dispatch/{alertId}")
    public Result<Map<String, Object>> autoDispatchPolice(@PathVariable String alertId) {
        return Result.success(predictionAlertService.autoDispatchPolice(alertId));
    }

    @GetMapping("/predict-alert/stats")
    public Result<Map<String, Object>> getPredictionAlertStats() {
        return Result.success(predictionAlertService.getAlertStats());
    }

    // ============================ 敏感区域检测 ============================

    @GetMapping("/sensitive/check")
    public Result<Map<String, Object>> checkSensitiveArea(
            @RequestParam double lng,
            @RequestParam double lat) {
        return Result.success(fenceService.checkSensitiveArea(lng, lat));
    }
}
