package com.police.vision.alarm.service;

import com.alibaba.fastjson2.JSON;
import com.police.vision.alarm.entity.DispatchContext;
import com.police.vision.alarm.entity.PoliceOfficer;
import com.police.vision.common.dto.MultiDispatchPlanDTO;
import com.police.vision.common.dto.OfficerEtaResultDTO;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RendezvousPlanningService {

    private final AmapRouteClientService amapRouteClientService;
    private final RedisUtil redisUtil;

    public static final int RENDEZVOUS_TYPE_WEIGHTED_CENTER = 1;
    public static final int RENDEZVOUS_TYPE_NEAREST_ROAD = 2;
    public static final int RENDEZVOUS_TYPE_FASTEST_POLICE = 3;
    public static final int RENDEZVOUS_TYPE_NEAR_ALARM = 4;

    public MultiDispatchPlanDTO calculateRendezvous(DispatchContext context,
                                                     List<PoliceOfficer> officers,
                                                     List<OfficerEtaResultDTO> allEtaResults) {
        if (officers == null || officers.size() < 2) {
            return null;
        }

        try {
            Map<Long, OfficerEtaResultDTO> etaMap = allEtaResults != null
                    ? allEtaResults.stream().collect(Collectors.toMap(OfficerEtaResultDTO::getPoliceId, e -> e, (a, b) -> a))
                    : new HashMap<>();

            List<CandidatePoint> candidates = new ArrayList<>();

            candidates.add(calculateWeightedCenterPoint(officers, etaMap, context));
            candidates.add(calculateAtFastestPolicePoint(officers, etaMap, context));
            candidates.add(calculateNearAlarmPoint(officers, etaMap, context));

            CandidatePoint best = candidates.stream()
                    .min(Comparator.comparingInt(c -> c.maxRendezvousEta + c.rendezvousToAlarmEta))
                    .orElse(candidates.get(0));

            int maxRendezvousEta = 0;
            List<OfficerEtaResultDTO> rendezvousEtaList = new ArrayList<>();
            for (PoliceOfficer officer : officers) {
                OfficerEtaResultDTO toRendezvous = amapRouteClientService.calculateOfficerEta(
                        officer.getId(), officer.getName(),
                        officer.getLongitude(), officer.getLatitude(),
                        best.lon, best.lat);
                rendezvousEtaList.add(toRendezvous);
                if (toRendezvous.getEtaSeconds() != null && toRendezvous.getEtaSeconds() > maxRendezvousEta) {
                    maxRendezvousEta = toRendezvous.getEtaSeconds();
                }
            }

            MultiDispatchPlanDTO plan = MultiDispatchPlanDTO.builder()
                    .alarmId(context.getAlarmId())
                    .alarmLongitude(context.getLongitude())
                    .alarmLatitude(context.getLatitude())
                    .alarmAddress(context.getAlarmAddress())
                    .rendezvousLongitude(best.lon)
                    .rendezvousLatitude(best.lat)
                    .rendezvousName(best.name)
                    .rendezvousAddress(best.address)
                    .rendezvousType(best.type)
                    .rendezvousToAlarmDistance(best.rendezvousToAlarmDistance)
                    .rendezvousToAlarmEta(best.rendezvousToAlarmEta)
                    .totalPoliceCount(officers.size())
                    .estimatedArrivalSeconds(maxRendezvousEta + best.rendezvousToAlarmEta)
                    .officerEtaList(rendezvousEtaList)
                    .planDescription(buildPlanDescription(officers, maxRendezvousEta, best))
                    .build();

            cacheRendezvousPlan(context.getAlarmId(), plan);
            return plan;

        } catch (Exception e) {
            log.error("计算会合点失败：{}", e.getMessage(), e);
            return buildFallbackPlan(context, officers);
        }
    }

    private CandidatePoint calculateWeightedCenterPoint(List<PoliceOfficer> officers,
                                                          Map<Long, OfficerEtaResultDTO> etaMap,
                                                          DispatchContext ctx) {
        BigDecimal sumLon = BigDecimal.ZERO;
        BigDecimal sumLat = BigDecimal.ZERO;
        double totalWeight = 0;

        for (PoliceOfficer officer : officers) {
            OfficerEtaResultDTO eta = etaMap.get(officer.getId());
            double etaFactor = 1.0;
            if (eta != null && eta.getEtaSeconds() != null && eta.getEtaSeconds() > 0) {
                etaFactor = Math.max(0.5, 5.0 - (double) eta.getEtaSeconds() / 300.0);
            }
            sumLon = sumLon.add(officer.getLongitude().multiply(BigDecimal.valueOf(etaFactor)));
            sumLat = sumLat.add(officer.getLatitude().multiply(BigDecimal.valueOf(etaFactor)));
            totalWeight += etaFactor;
        }

        BigDecimal centerLon = sumLon.divide(BigDecimal.valueOf(totalWeight), 8, RoundingMode.HALF_UP);
        BigDecimal centerLat = sumLat.divide(BigDecimal.valueOf(totalWeight), 8, RoundingMode.HALF_UP);

        BigDecimal alarmLon = ctx.getLongitude();
        BigDecimal alarmLat = ctx.getLatitude();

        BigDecimal distRatio = BigDecimal.valueOf(0.7);
        BigDecimal rendezvousLon = centerLon.add(alarmLon.subtract(centerLon).multiply(distRatio));
        BigDecimal rendezvousLat = centerLat.add(alarmLat.subtract(centerLat).multiply(distRatio));

        OfficerEtaResultDTO rendezvousToAlarm = amapRouteClientService.calculateOfficerEta(
                -1L, "rendezvous",
                rendezvousLon, rendezvousLat,
                alarmLon, alarmLat);

        int maxRendezvousEta = 0;
        for (PoliceOfficer officer : officers) {
            OfficerEtaResultDTO toRdv = amapRouteClientService.calculateOfficerEta(
                    officer.getId(), officer.getName(),
                    officer.getLongitude(), officer.getLatitude(),
                    rendezvousLon, rendezvousLat);
            if (toRdv.getEtaSeconds() != null && toRdv.getEtaSeconds() > maxRendezvousEta) {
                maxRendezvousEta = toRdv.getEtaSeconds();
            }
        }

        CandidatePoint cp = new CandidatePoint();
        cp.lon = rendezvousLon;
        cp.lat = rendezvousLat;
        cp.name = "加权中心会合点";
        cp.address = String.format("距警情%.1fkm的集合点",
                rendezvousToAlarm.getRoadDistance() != null ? rendezvousToAlarm.getRoadDistance().doubleValue() / 1000.0 : 0);
        cp.type = RENDEZVOUS_TYPE_WEIGHTED_CENTER;
        cp.maxRendezvousEta = maxRendezvousEta;
        cp.rendezvousToAlarmEta = rendezvousToAlarm.getEtaSeconds() != null ? rendezvousToAlarm.getEtaSeconds() : 0;
        cp.rendezvousToAlarmDistance = rendezvousToAlarm.getRoadDistance();
        return cp;
    }

    private CandidatePoint calculateAtFastestPolicePoint(List<PoliceOfficer> officers,
                                                          Map<Long, OfficerEtaResultDTO> etaMap,
                                                          DispatchContext ctx) {
        PoliceOfficer fastest = officers.stream()
                .min(Comparator.comparingInt(o -> {
                    OfficerEtaResultDTO e = etaMap.get(o.getId());
                    return e != null && e.getEtaSeconds() != null ? e.getEtaSeconds() : Integer.MAX_VALUE;
                })).orElse(officers.get(0));

        OfficerEtaResultDTO eta = etaMap.get(fastest.getId());
        BigDecimal lon = fastest.getLongitude();
        BigDecimal lat = fastest.getLatitude();
        if (eta != null && eta.getRoadDistance() != null) {
            double km = eta.getRoadDistance().doubleValue() / 1000.0;
            if (km >= 0.5) {
                double travelRatio = Math.min(0.9, 300.0 / (double) (eta.getEtaSeconds() != null ? eta.getEtaSeconds() : 600));
                lon = fastest.getLongitude().add(ctx.getLongitude().subtract(fastest.getLongitude())
                        .multiply(BigDecimal.valueOf(travelRatio)));
                lat = fastest.getLatitude().add(ctx.getLatitude().subtract(fastest.getLatitude())
                        .multiply(BigDecimal.valueOf(travelRatio)));
            }
        }

        OfficerEtaResultDTO rdvToAlarm = amapRouteClientService.calculateOfficerEta(
                -1L, "rendezvous", lon, lat, ctx.getLongitude(), ctx.getLatitude());

        int maxEta = 0;
        for (PoliceOfficer officer : officers) {
            OfficerEtaResultDTO toRdv = amapRouteClientService.calculateOfficerEta(
                    officer.getId(), officer.getName(),
                    officer.getLongitude(), officer.getLatitude(), lon, lat);
            if (toRdv.getEtaSeconds() != null && toRdv.getEtaSeconds() > maxEta) {
                maxEta = toRdv.getEtaSeconds();
            }
        }

        CandidatePoint cp = new CandidatePoint();
        cp.lon = lon;
        cp.lat = lat;
        cp.name = "最快警力途径会合点";
        cp.address = String.format("在最快警员%s前进路径上", fastest.getName());
        cp.type = RENDEZVOUS_TYPE_FASTEST_POLICE;
        cp.maxRendezvousEta = maxEta;
        cp.rendezvousToAlarmEta = rdvToAlarm.getEtaSeconds() != null ? rdvToAlarm.getEtaSeconds() : 0;
        cp.rendezvousToAlarmDistance = rdvToAlarm.getRoadDistance();
        return cp;
    }

    private CandidatePoint calculateNearAlarmPoint(List<PoliceOfficer> officers,
                                                    Map<Long, OfficerEtaResultDTO> etaMap,
                                                    DispatchContext ctx) {
        BigDecimal lon = ctx.getLongitude().add(BigDecimal.valueOf((Math.random() - 0.5) * 0.005));
        BigDecimal lat = ctx.getLatitude().add(BigDecimal.valueOf((Math.random() - 0.5) * 0.005));

        OfficerEtaResultDTO rdvToAlarm = amapRouteClientService.calculateOfficerEta(
                -1L, "rendezvous", lon, lat, ctx.getLongitude(), ctx.getLatitude());

        int maxEta = 0;
        for (PoliceOfficer officer : officers) {
            OfficerEtaResultDTO toRdv = amapRouteClientService.calculateOfficerEta(
                    officer.getId(), officer.getName(),
                    officer.getLongitude(), officer.getLatitude(), lon, lat);
            if (toRdv.getEtaSeconds() != null && toRdv.getEtaSeconds() > maxEta) {
                maxEta = toRdv.getEtaSeconds();
            }
        }

        CandidatePoint cp = new CandidatePoint();
        cp.lon = lon;
        cp.lat = lat;
        cp.name = "警情周边会合点";
        cp.address = "警情发生地附近安全集结点";
        cp.type = RENDEZVOUS_TYPE_NEAR_ALARM;
        cp.maxRendezvousEta = maxEta;
        cp.rendezvousToAlarmEta = rdvToAlarm.getEtaSeconds() != null ? rdvToAlarm.getEtaSeconds() : 120;
        cp.rendezvousToAlarmDistance = rdvToAlarm.getRoadDistance();
        return cp;
    }

    private MultiDispatchPlanDTO buildFallbackPlan(DispatchContext ctx, List<PoliceOfficer> officers) {
        return MultiDispatchPlanDTO.builder()
                .alarmId(ctx.getAlarmId())
                .alarmLongitude(ctx.getLongitude())
                .alarmLatitude(ctx.getLatitude())
                .rendezvousLongitude(ctx.getLongitude())
                .rendezvousLatitude(ctx.getLatitude())
                .rendezvousName("警情发生地")
                .rendezvousAddress(ctx.getAlarmAddress())
                .rendezvousType(RENDEZVOUS_TYPE_NEAR_ALARM)
                .totalPoliceCount(officers.size())
                .estimatedArrivalSeconds(600)
                .officerEtaList(Collections.emptyList())
                .planDescription("会合点计算异常，默认直接前往警情发生地集结")
                .build();
    }

    private String buildPlanDescription(List<PoliceOfficer> officers, int maxRdvEta, CandidatePoint best) {
        String names = officers.stream().limit(3)
                .map(PoliceOfficer::getName)
                .collect(Collectors.joining("、"));
        int more = officers.size() - 3;
        if (more > 0) names += "等" + officers.size() + "人";
        return String.format("【联合出警】%s 在[%s]会合（各自到位约%s分钟），再前往警情（%s）。总预计%s分钟到达。",
                names,
                best.name,
                maxRdvEta / 60,
                formatEta(best.rendezvousToAlarmEta),
                (maxRdvEta + best.rendezvousToAlarmEta) / 60);
    }

    private void cacheRendezvousPlan(Long alarmId, MultiDispatchPlanDTO plan) {
        String key = RedisConstant.DISPATCH_TRACK_PREFIX + alarmId + ":rendezvous";
        redisUtil.setObject(key, plan, RedisConstant.DISPATCH_TRACK_EXPIRE, java.util.concurrent.TimeUnit.SECONDS);
    }

    public MultiDispatchPlanDTO getCachedRendezvousPlan(Long alarmId) {
        String key = RedisConstant.DISPATCH_TRACK_PREFIX + alarmId + ":rendezvous";
        return redisUtil.getObject(key, MultiDispatchPlanDTO.class);
    }

    private static String formatEta(Integer seconds) {
        if (seconds == null) return "-";
        int m = seconds / 60;
        int s = seconds % 60;
        if (m < 60) return m + "分" + (s > 0 ? s + "秒" : "钟");
        return (m / 60) + "小时" + (m % 60) + "分";
    }

    static class CandidatePoint {
        BigDecimal lon;
        BigDecimal lat;
        String name;
        String address;
        int type;
        int maxRendezvousEta;
        int rendezvousToAlarmEta;
        BigDecimal rendezvousToAlarmDistance;
    }
}
