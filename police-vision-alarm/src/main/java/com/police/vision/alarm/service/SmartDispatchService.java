package com.police.vision.alarm.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.alarm.entity.AlarmOrder;
import com.police.vision.alarm.entity.DispatchContext;
import com.police.vision.alarm.entity.DispatchRecord;
import com.police.vision.alarm.entity.PoliceOfficer;
import com.police.vision.alarm.mapper.AlarmOrderMapper;
import com.police.vision.alarm.mapper.DispatchRecordMapper;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.*;
import com.police.vision.common.entity.DispatchTrafficSnapshot;
import com.police.vision.alarm.mapper.DispatchTrafficSnapshotMapper;
import com.police.vision.common.enums.AlarmStatusEnum;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartDispatchService {

    private final AmapRouteClientService amapRouteClientService;
    private final RendezvousPlanningService rendezvousPlanningService;
    private final DispatchTrafficSnapshotMapper snapshotMapper;
    private final DispatchRecordMapper dispatchRecordMapper;
    private final AlarmOrderMapper alarmOrderMapper;
    private final RedisUtil redisUtil;

    private static final double ALPHA_ETA = 0.60;
    private static final double ALPHA_DIST = 0.15;
    private static final double ALPHA_TRAFFIC = 0.10;
    private static final double ALPHA_STATUS = 0.10;
    private static final double ALPHA_EQUIPMENT = 0.05;

    public DispatchContext calculateSmartDispatch(DispatchContext context) {
        long startTime = System.currentTimeMillis();
        List<PoliceOfficer> officers = context.getAvailableOfficers();
        if (officers == null || officers.isEmpty()) {
            context.setRecommendedOfficers(Collections.emptyList());
            context.setDispatchSuggestion("附近暂无可用警力");
            return context;
        }

        AmapTrafficStatusDTO trafficStatus = amapRouteClientService.getTrafficAround(
                context.getLongitude(), context.getLatitude(), 5000);
        int overallTrafficLevel = trafficStatus.getTrafficLevel();
        context.setAvgTrafficLevel(BigDecimal.valueOf(overallTrafficLevel));
        context.setRawTrafficData(trafficStatus);

        List<OfficerEtaResultDTO> etaResults = new ArrayList<>();
        int fastestEta = Integer.MAX_VALUE;
        Long fastestPoliceId = null;
        String fastestPoliceName = null;

        for (PoliceOfficer officer : officers) {
            double straightDistKm = AmapRouteClientService.calculateStraightDistanceKm(
                    officer.getLongitude(), officer.getLatitude(),
                    context.getLongitude(), context.getLatitude());

            if (straightDistKm > calculateMaxDistanceByPriority(context.getPriority())) {
                continue;
            }

            OfficerEtaResultDTO eta = amapRouteClientService.calculateOfficerEta(
                    officer.getId(), officer.getName(),
                    officer.getLongitude(), officer.getLatitude(),
                    context.getLongitude(), context.getLatitude());

            etaResults.add(eta);
            context.getOfficerEtaMap().put(officer.getId(), eta);

            officer.setEtaResult(eta);
            officer.setEtaSeconds(eta.getEtaSeconds());
            officer.setRoadDistance(eta.getRoadDistance());
            officer.setTrafficLevel(eta.getTrafficLevel());
            officer.setRoutePolyline(eta.getRoutePolyline());
            officer.setDistance(straightDistKm);

            double score = calculateScore(officer, eta, overallTrafficLevel);
            officer.setDispatchScore(score);

            if (eta.getEtaSeconds() != null && eta.getEtaSeconds() < fastestEta) {
                fastestEta = eta.getEtaSeconds();
                fastestPoliceId = officer.getId();
                fastestPoliceName = officer.getName();
            }
        }

        if (etaResults.isEmpty()) {
            context.setRecommendedOfficers(Collections.emptyList());
            context.setDispatchSuggestion("附近范围内无可用警力，请扩大搜索半径");
            return context;
        }

        int requiredCount = calculateRequiredOfficerCount(context);
        List<PoliceOfficer> sortedOfficers = officers.stream()
                .filter(o -> o.getDispatchScore() != null)
                .sorted(Comparator
                        .comparing((PoliceOfficer o) -> o.getEtaSeconds() != null ? o.getEtaSeconds() : Integer.MAX_VALUE)
                        .thenComparing(PoliceOfficer::getDispatchScore, Comparator.reverseOrder()))
                .limit(requiredCount)
                .collect(Collectors.toList());

        for (int i = 0; i < sortedOfficers.size(); i++) {
            sortedOfficers.get(i).setDispatchRank(i + 1);
        }

        context.setRecommendedOfficers(sortedOfficers);
        context.setFastestEtaSeconds(fastestEta == Integer.MAX_VALUE ? null : fastestEta);
        context.setFastestPoliceId(fastestPoliceId);
        context.setDispatchAlgorithm("ETA_WEIGHTED_SCORE");
        context.setDispatchVersion("2.0.0-smart");
        context.setCalculateTime(LocalDateTime.now());

        if (fastestEta < Integer.MAX_VALUE && fastestPoliceId != null) {
            PoliceOfficer fastestOfficer = sortedOfficers.stream()
                    .filter(o -> fastestPoliceId.equals(o.getId()))
                    .findFirst().orElse(null);
            if (fastestOfficer != null && fastestOfficer.getDistance() != null) {
                double straightKm = fastestOfficer.getDistance();
                int straightEtaEstimate = (int) (straightKm * 180);
                if (straightEtaEstimate > 0 && fastestEta > 0) {
                    double savedPercent = (1.0 - (double) fastestEta / straightEtaEstimate) * 100.0;
                    context.setSavedEtaPercent(BigDecimal.valueOf(savedPercent).setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

        int avgEta = (int) sortedOfficers.stream()
                .mapToInt(o -> o.getEtaSeconds() != null ? o.getEtaSeconds() : 0)
                .average().orElse(0);

        String suggestion = String.format("推荐%s名警力，最快ETA%s（警员%s），平均ETA%s，路况等级%s，综合评分择优，建议派单。智能算法v2.0",
                sortedOfficers.size(),
                context.getFastestEtaSeconds() != null ? formatEta(context.getFastestEtaSeconds()) : "-",
                fastestPoliceName != null ? fastestPoliceName : "-",
                formatEta(avgEta),
                trafficLevelText(overallTrafficLevel));
        context.setDispatchSuggestion(suggestion);

        if (sortedOfficers.size() >= 3) {
            MultiDispatchPlanDTO plan = rendezvousPlanningService.calculateRendezvous(
                    context, sortedOfficers, etaResults);
            context.setMultiDispatchPlan(plan);
            log.info("多名警力联合出警，规划会合点：{}", plan.getRendezvousName());
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("智能派单计算完成：alarmId={}, 推荐警力数={}, 耗时={}ms",
                context.getAlarmId(), sortedOfficers.size(), cost);
        return context;
    }

    @Transactional(rollbackFor = Exception.class)
    public DispatchTrafficSnapshot saveTrafficSnapshot(DispatchContext context,
                                                       Long dispatchId,
                                                       String dispatchNo) {
        DispatchTrafficSnapshot snapshot = new DispatchTrafficSnapshot();
        snapshot.setId(SnowflakeIdUtil.nextId());
        snapshot.setSnapshotId("SNAP-" + SnowflakeIdUtil.nextId());
        snapshot.setDispatchId(dispatchId);
        snapshot.setDispatchNo(dispatchNo);
        snapshot.setAlarmId(context.getAlarmId());
        snapshot.setSnapshotType(context.getDispatchAlgorithm());
        snapshot.setAlarmLongitude(context.getLongitude());
        snapshot.setAlarmLatitude(context.getLatitude());
        snapshot.setPoliceCount(context.getRecommendedOfficers() != null ? context.getRecommendedOfficers().size() : 0);

        List<Long> policeIds = context.getRecommendedOfficers() != null
                ? context.getRecommendedOfficers().stream().map(PoliceOfficer::getId).collect(Collectors.toList())
                : Collections.emptyList();
        snapshot.setPoliceIdsStr(JSON.toJSONString(policeIds));

        List<OfficerEtaResultDTO> etaList = new ArrayList<>(context.getOfficerEtaMap().values());
        snapshot.setOfficerEtaData(JSON.toJSONString(etaList));

        Map<String, String> routeMap = new HashMap<>();
        for (PoliceOfficer o : context.getRecommendedOfficers() != null ? context.getRecommendedOfficers() : Collections.<PoliceOfficer>emptyList()) {
            routeMap.put(String.valueOf(o.getId()), o.getRoutePolyline());
        }
        snapshot.setRoutePolylineData(JSON.toJSONString(routeMap));

        if (context.getMultiDispatchPlan() != null) {
            snapshot.setMultiDispatchPlanData(JSON.toJSONString(context.getMultiDispatchPlan()));
            snapshot.setRendezvousLongitude(context.getMultiDispatchPlan().getRendezvousLongitude());
            snapshot.setRendezvousLatitude(context.getMultiDispatchPlan().getRendezvousLatitude());
            snapshot.setRendezvousName(context.getMultiDispatchPlan().getRendezvousName());
            snapshot.setRendezvousEtaSeconds(context.getMultiDispatchPlan().getRendezvousToAlarmEta());
        }

        snapshot.setAvgTrafficLevel(context.getAvgTrafficLevel());
        snapshot.setFastestEtaSeconds(context.getFastestEtaSeconds());
        snapshot.setFastestPoliceId(context.getFastestPoliceId());
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshot.setSource("SMART_DISPATCH");

        if (context.getRawTrafficData() != null) {
            snapshot.setTrafficData(JSON.toJSONString(context.getRawTrafficData()));
        }

        if (context.getSavedEtaPercent() != null) {
            snapshot.setRemark(String.format("ETA节省%.1f%%", context.getSavedEtaPercent().doubleValue()));
        }

        List<OfficerEtaResultDTO> allEtas = new ArrayList<>(context.getOfficerEtaMap().values());
        if (!allEtas.isEmpty()) {
            int avgEta = (int) allEtas.stream().mapToInt(e -> e.getEtaSeconds() != null ? e.getEtaSeconds() : 0).average().orElse(0);
            BigDecimal totalDist = allEtas.stream()
                    .map(OfficerEtaResultDTO::getRoadDistance)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            snapshot.setAvgEtaSeconds(avgEta);
            snapshot.setTotalRoadDistance(totalDist);
        }

        snapshotMapper.insert(snapshot);
        context.setTrafficSnapshotId(snapshot.getSnapshotId());
        log.info("路况快照保存成功：snapshotId={}, dispatchId={}", snapshot.getSnapshotId(), dispatchId);
        return snapshot;
    }

    public DispatchTrafficSnapshot getLatestSnapshot(Long dispatchId) {
        List<DispatchTrafficSnapshot> list = snapshotMapper.selectByDispatchId(dispatchId);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    private double calculateScore(PoliceOfficer officer, OfficerEtaResultDTO eta, int trafficLevel) {
        double etaScore = 0;
        if (eta.getEtaSeconds() != null) {
            double maxEta = 1800.0;
            double normalized = Math.max(0, 1.0 - eta.getEtaSeconds() / maxEta);
            etaScore = normalized * 100;
        }

        double distScore = 0;
        if (officer.getDistance() != null) {
            double maxDist = 10.0;
            double normalized = Math.max(0, 1.0 - officer.getDistance() / maxDist);
            distScore = normalized * 100;
        }

        double trafficScore = (5.0 - Math.min(5, Math.max(1,
                eta.getTrafficLevel() != null ? eta.getTrafficLevel() : 1))) / 4.0 * 100;

        int status = officer.getStatus() != null ? officer.getStatus() : 1;
        double statusScore = status == 1 ? 100 : (status == 2 ? 70 : (status == 3 ? 50 : 0));

        double equipmentScore = 100;
        if (officer.getVehicleType() != null) {
            equipmentScore = officer.getVehicleType().contains("汽车") ? 100
                    : (officer.getVehicleType().contains("摩托") ? 80 : 60);
        }

        double finalScore = ALPHA_ETA * etaScore
                + ALPHA_DIST * distScore
                + ALPHA_TRAFFIC * trafficScore
                + ALPHA_STATUS * statusScore
                + ALPHA_EQUIPMENT * equipmentScore;

        if (log.isDebugEnabled()) {
            log.debug("警员评分计算：id={}, name={}, score={} (eta={}, dist={}, traffic={}, status={}, equip={})",
                    officer.getId(), officer.getName(), String.format("%.2f", finalScore),
                    String.format("%.1f", ALPHA_ETA * etaScore), String.format("%.1f", ALPHA_DIST * distScore),
                    String.format("%.1f", ALPHA_TRAFFIC * trafficScore), String.format("%.1f", ALPHA_STATUS * statusScore),
                    String.format("%.1f", ALPHA_EQUIPMENT * equipmentScore));
        }
        return finalScore;
    }

    private int calculateRequiredOfficerCount(DispatchContext ctx) {
        int type = ctx.getAlarmType() != null ? ctx.getAlarmType() : 0;
        int priority = ctx.getPriority() != null ? ctx.getPriority() : 3;

        int base;
        if (type == 1 || type == 11 || type == 8) base = 4;
        else if (type == 3 || type == 12) base = 3;
        else if (type == 2 || type == 6) base = 2;
        else base = 2;

        if (priority == 1) base += 2;
        else if (priority == 2) base += 1;

        if (ctx.getRequiredOfficerCount() > 0) {
            base = Math.max(base, ctx.getRequiredOfficerCount());
        }
        return Math.max(1, Math.min(base, 8));
    }

    private double calculateMaxDistanceByPriority(Integer priority) {
        if (priority == null) priority = 3;
        return switch (priority) {
            case 1 -> 8.0;
            case 2 -> 6.0;
            case 3 -> 4.0;
            default -> 3.0;
        };
    }

    public static String formatEta(Integer seconds) {
        if (seconds == null) return "-";
        int m = seconds / 60;
        int s = seconds % 60;
        if (m < 60) return m + "分" + (s > 0 ? s + "秒" : "钟");
        return (m / 60) + "小时" + (m % 60) + "分";
    }

    public static String trafficLevelText(int level) {
        return switch (level) {
            case 1 -> "畅通";
            case 2 -> "缓行";
            case 3 -> "拥堵";
            case 4 -> "严重拥堵";
            default -> "未知";
        };
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void scheduledRefreshInTransitTraffic() {
        LambdaQueryWrapper<DispatchRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DispatchRecord::getDispatchStatus, 0)
                .isNotNull(DispatchRecord::getDispatchAlgorithm)
                .gt(DispatchRecord::getCreateTime, LocalDateTime.now().minusHours(2));
        List<DispatchRecord> inTransitDispatches = dispatchRecordMapper.selectList(wrapper);
        if (inTransitDispatches == null || inTransitDispatches.isEmpty()) return;

        int refreshed = 0;
        for (DispatchRecord dispatch : inTransitDispatches) {
            try {
                AlarmOrder alarm = alarmOrderMapper.selectById(dispatch.getAlarmId());
                if (alarm == null || alarm.getLongitude() == null || alarm.getLatitude() == null) continue;

                AmapTrafficStatusDTO freshTraffic = amapRouteClientService.getTrafficAround(
                        alarm.getLongitude(), alarm.getLatitude(), 5000);

                DispatchTrafficSnapshot snapshot = new DispatchTrafficSnapshot();
                snapshot.setId(SnowflakeIdUtil.nextId());
                snapshot.setSnapshotId("SNAP-REFRESH-" + SnowflakeIdUtil.nextId());
                snapshot.setDispatchId(dispatch.getId());
                snapshot.setDispatchNo(dispatch.getDispatchNo());
                snapshot.setAlarmId(dispatch.getAlarmId());
                snapshot.setSnapshotType("PERIODIC_REFRESH");
                snapshot.setSnapshotTime(LocalDateTime.now());
                snapshot.setAlarmLongitude(alarm.getLongitude());
                snapshot.setAlarmLatitude(alarm.getLatitude());
                snapshot.setAvgTrafficLevel(BigDecimal.valueOf(freshTraffic.getTrafficLevel()));
                snapshot.setTrafficData(JSON.toJSONString(freshTraffic));
                snapshot.setSource("SCHEDULED_REFRESH");

                if (dispatch.getFastestPoliceId() != null) {
                    String locationKey = RedisConstant.POLICE_LOCATION_PREFIX + dispatch.getFastestPoliceId();
                    String locationStr = redisUtil.get(locationKey);
                    if (locationStr != null) {
                        com.police.vision.common.entity.GpsLocation gps =
                                JSON.parseObject(locationStr, com.police.vision.common.entity.GpsLocation.class);
                        if (gps != null && gps.getLongitude() != null && gps.getLatitude() != null) {
                            OfficerEtaResultDTO freshEta = amapRouteClientService.calculateOfficerEta(
                                    dispatch.getFastestPoliceId(), null,
                                    gps.getLongitude(), gps.getLatitude(),
                                    alarm.getLongitude(), alarm.getLatitude());
                            snapshot.setFastestEtaSeconds(freshEta.getEtaSeconds());
                            snapshot.setOfficerEtaData(JSON.toJSONString(Collections.singletonList(freshEta)));

                            if (dispatch.getFastestEtaSeconds() != null && freshEta.getEtaSeconds() != null) {
                                int prevEta = dispatch.getFastestEtaSeconds();
                                int newEta = freshEta.getEtaSeconds();
                                double changePercent = ((double) (newEta - prevEta) / prevEta) * 100.0;
                                snapshot.setRemark(String.format("路况刷新：ETA变化%.1f%%（%ds→%ds），路况等级%d",
                                        changePercent, prevEta, newEta, freshTraffic.getTrafficLevel()));

                                if (Math.abs(changePercent) > 30) {
                                    dispatch.setFastestEtaSeconds(newEta);
                                    dispatch.setAvgTrafficLevel(BigDecimal.valueOf(freshTraffic.getTrafficLevel()));
                                    dispatchRecordMapper.updateById(dispatch);
                                }
                            }
                        }
                    }
                }

                snapshotMapper.insert(snapshot);
                refreshed++;

                String refreshKey = "dispatch:traffic_refresh:" + dispatch.getId();
                redisUtil.set(refreshKey, String.valueOf(System.currentTimeMillis()),
                        RedisConstant.DISPATCH_TRACK_EXPIRE, TimeUnit.SECONDS);

            } catch (Exception e) {
                log.warn("在途警情{}路况刷新失败：{}", dispatch.getId(), e.getMessage());
            }
        }
        if (refreshed > 0) {
            log.info("在途警情路况刷新完成：刷新数={}/{}", refreshed, inTransitDispatches.size());
        }
    }
}
