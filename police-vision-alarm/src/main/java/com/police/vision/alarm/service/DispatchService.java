package com.police.vision.alarm.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.alarm.entity.AlarmHandleLog;
import com.police.vision.alarm.entity.AlarmOrder;
import com.police.vision.alarm.entity.DispatchContext;
import com.police.vision.alarm.entity.DispatchRecord;
import com.police.vision.alarm.entity.PoliceOfficer;
import com.police.vision.alarm.mapper.AlarmHandleLogMapper;
import com.police.vision.alarm.mapper.AlarmOrderMapper;
import com.police.vision.alarm.mapper.DispatchRecordMapper;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.AlarmDispatchDTO;
import com.police.vision.common.dto.AlarmStatusUpdateDTO;
import com.police.vision.common.dto.OfficerEtaResultDTO;
import com.police.vision.common.entity.DispatchTrafficSnapshot;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.enums.AlarmStatusEnum;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final DispatchRecordMapper dispatchRecordMapper;
    private final AlarmOrderMapper alarmOrderMapper;
    private final AlarmHandleLogMapper alarmHandleLogMapper;
    private final DispatchRuleEngine dispatchRuleEngine;
    private final SmartDispatchService smartDispatchService;
    private final YawDetectionService yawDetectionService;
    private final RedisUtil redisUtil;
    private final RocketMQTemplate rocketMQTemplate;
    private final MqUtil mqUtil;

    private static final long DISPATCH_LOCK_EXPIRE = 30;
    private static final long DISPATCH_TIMEOUT_MINUTES = 5;

    @Transactional(rollbackFor = Exception.class)
    public DispatchRecord autoDispatch(Long alarmId) {
        return autoDispatch(alarmId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public DispatchRecord autoDispatch(Long alarmId, boolean useSmartEta) {
        String lockKey = RedisConstant.ALARM_DISPATCH_LOCK_PREFIX + alarmId;
        String lockValue = UUID.randomUUID().toString();

        try {
            if (!redisUtil.tryLock(lockKey, lockValue, DISPATCH_LOCK_EXPIRE, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.OPERATION_ERROR, "正在派单中，请稍后重试");
            }

            AlarmOrder alarmOrder = alarmOrderMapper.selectById(alarmId);
            if (alarmOrder == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "警情不存在");
            }
            if (!AlarmStatusEnum.PENDING.getCode().equals(alarmOrder.getAlarmStatus())) {
                throw new BusinessException(ResultCode.OPERATION_ERROR, "当前警情状态不允许派单");
            }

            List<PoliceOfficer> availableOfficers = getAvailableOfficers();
            DispatchContext context = new DispatchContext();
            context.setAlarmId(alarmId);
            context.setAlarmType(alarmOrder.getAlarmType());
            context.setPriority(alarmOrder.getPriority());
            context.setLongitude(alarmOrder.getLongitude());
            context.setLatitude(alarmOrder.getLatitude());
            context.setAlarmAddress(alarmOrder.getAddress());
            context.setUseSmartEta(useSmartEta);
            context.setDispatchMode(useSmartEta ? "SMART" : "NORMAL");
            context.setAvailableOfficers(availableOfficers);

            DispatchContext result;
            if (useSmartEta) {
                result = smartDispatchService.calculateSmartDispatch(context);
            } else {
                result = dispatchRuleEngine.calculateDispatch(context);
            }
            List<PoliceOfficer> recommendedOfficers = result.getRecommendedOfficers();

            if (recommendedOfficers.isEmpty()) {
                throw new BusinessException(ResultCode.OPERATION_ERROR, "附近暂无可用警力，请人工派单");
            }

            List<Long> policeIds = recommendedOfficers.stream()
                    .map(PoliceOfficer::getId)
                    .collect(Collectors.toList());

            DispatchRecord record = createDispatchRecord(alarmId, policeIds, null,
                    result.getDispatchSuggestion(),
                    result.getPriority() != null ? result.getPriority() : alarmOrder.getPriority(),
                    result);

            DispatchTrafficSnapshot snapshot = null;
            if (useSmartEta) {
                snapshot = smartDispatchService.saveTrafficSnapshot(result, record.getId(), record.getDispatchNo());
                if (snapshot != null) {
                    record.setTrafficSnapshotId(snapshot.getSnapshotId());
                    dispatchRecordMapper.updateById(record);
                }
            }

            AlarmStatusUpdateDTO statusDTO = new AlarmStatusUpdateDTO();
            statusDTO.setAlarmId(alarmId);
            statusDTO.setStatus(AlarmStatusEnum.DISPATCHED.getCode());
            statusDTO.setRemark("自动派单：" + result.getDispatchSuggestion());
            updateAlarmStatusInternal(statusDTO);

            sendDispatchTimeoutMessage(alarmId);
            sendDispatchNotifications(record, alarmOrder, policeIds, result, recommendedOfficers);

            if (useSmartEta) {
                for (PoliceOfficer officer : recommendedOfficers) {
                    yawDetectionService.registerDispatchYawCheck(
                            record.getId(),
                            officer.getId(),
                            officer.getLongitude(),
                            officer.getLatitude(),
                            officer.getRoutePolyline());
                }
                log.info("已为派单{}注册{}个偏航检测监听", record.getDispatchNo(), recommendedOfficers.size());
            }

            log.info("自动派单成功，警情ID：{}，派单警力：{}，算法：{}，最快ETA：{}秒",
                    alarmId, policeIds,
                    result.getDispatchAlgorithm(),
                    result.getFastestEtaSeconds());
            return record;

        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public DispatchRecord manualDispatch(AlarmDispatchDTO dto) {
        AlarmOrder alarmOrder = alarmOrderMapper.selectById(dto.getAlarmId());
        if (alarmOrder == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "警情不存在");
        }
        if (!AlarmStatusEnum.PENDING.getCode().equals(alarmOrder.getAlarmStatus())
                && !AlarmStatusEnum.ESCALATED.getCode().equals(alarmOrder.getAlarmStatus())) {
            throw new BusinessException(ResultCode.OPERATION_ERROR, "当前警情状态不允许派单");
        }

        DispatchContext context = new DispatchContext();
        context.setAlarmId(dto.getAlarmId());
        context.setAlarmType(alarmOrder.getAlarmType());
        context.setPriority(dto.getPriority() != null ? dto.getPriority() : alarmOrder.getPriority());
        context.setLongitude(alarmOrder.getLongitude());
        context.setLatitude(alarmOrder.getLatitude());
        context.setAlarmAddress(alarmOrder.getAddress());
        context.setUseSmartEta(Boolean.TRUE.equals(dto.getUseSmartEta()));
        context.setDispatchMode("MANUAL");

        if (dto.getUseSmartEta() != null && dto.getUseSmartEta()) {
            List<PoliceOfficer> allOfficers = getAvailableOfficers();
            List<PoliceOfficer> selectedOfficers = allOfficers.stream()
                    .filter(o -> dto.getPoliceIds().contains(o.getId()))
                    .collect(Collectors.toList());
            if (selectedOfficers.size() < dto.getPoliceIds().size()) {
                for (Long pid : dto.getPoliceIds()) {
                    if (selectedOfficers.stream().noneMatch(o -> pid.equals(o.getId()))) {
                        PoliceOfficer po = new PoliceOfficer();
                        po.setId(pid);
                        po.setStatus(1);
                        po.setLongitude(alarmOrder.getLongitude());
                        po.setLatitude(alarmOrder.getLatitude());
                        selectedOfficers.add(po);
                    }
                }
            }
            context.setAvailableOfficers(selectedOfficers);
            context = smartDispatchService.calculateSmartDispatch(context);
        }

        String remark = dto.getDispatchRemark();
        if (context.getDispatchSuggestion() != null && dto.getUseSmartEta() != null && dto.getUseSmartEta()) {
            remark = (remark == null ? "" : remark + " | ") + context.getDispatchSuggestion();
        }

        DispatchRecord record = createDispatchRecord(dto.getAlarmId(), dto.getPoliceIds(),
                dto.getCommanderId(), remark,
                dto.getPriority() != null ? dto.getPriority() : alarmOrder.getPriority(),
                context);

        if (dto.getUseSmartEta() != null && dto.getUseSmartEta()) {
            DispatchTrafficSnapshot snapshot = smartDispatchService.saveTrafficSnapshot(context, record.getId(), record.getDispatchNo());
            if (snapshot != null) {
                record.setTrafficSnapshotId(snapshot.getSnapshotId());
                dispatchRecordMapper.updateById(record);
            }
            List<PoliceOfficer> officers = context.getRecommendedOfficers() != null
                    ? context.getRecommendedOfficers()
                    : Collections.emptyList();
            for (PoliceOfficer officer : officers) {
                yawDetectionService.registerDispatchYawCheck(
                        record.getId(), officer.getId(),
                        officer.getLongitude(), officer.getLatitude(),
                        officer.getRoutePolyline());
            }
        }

        AlarmStatusUpdateDTO statusDTO = new AlarmStatusUpdateDTO();
        statusDTO.setAlarmId(dto.getAlarmId());
        statusDTO.setStatus(AlarmStatusEnum.DISPATCHED.getCode());
        statusDTO.setRemark("人工派单：" + dto.getDispatchRemark());
        updateAlarmStatusInternal(statusDTO);

        sendDispatchTimeoutMessage(dto.getAlarmId());
        sendDispatchNotifications(record, alarmOrder, dto.getPoliceIds(), context,
                context.getRecommendedOfficers() != null ? context.getRecommendedOfficers() : Collections.emptyList());

        log.info("人工派单成功，警情ID：{}，派单警力：{}", dto.getAlarmId(), dto.getPoliceIds());
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public DispatchRecord recalcDispatch(Long alarmId, Long commanderId, String reason) {
        String lockKey = RedisConstant.ALARM_DISPATCH_LOCK_PREFIX + alarmId;
        String lockValue = UUID.randomUUID().toString();
        try {
            if (!redisUtil.tryLock(lockKey, lockValue, DISPATCH_LOCK_EXPIRE, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.OPERATION_ERROR, "正在派单中，请稍后重试");
            }
            DispatchRecord prev = getDispatchRecord(alarmId);
            if (prev != null && prev.getPoliceIds() != null) {
                for (Long pid : prev.getPoliceIds()) {
                    yawDetectionService.unregisterDispatchYawCheck(prev.getId(), pid);
                }
            }

            AlarmOrder alarmOrder = alarmOrderMapper.selectById(alarmId);
            if (alarmOrder == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "警情不存在");
            }

            List<PoliceOfficer> availableOfficers = getAvailableOfficers();
            DispatchContext context = new DispatchContext();
            context.setAlarmId(alarmId);
            context.setAlarmType(alarmOrder.getAlarmType());
            context.setPriority(alarmOrder.getPriority());
            context.setLongitude(alarmOrder.getLongitude());
            context.setLatitude(alarmOrder.getLatitude());
            context.setAlarmAddress(alarmOrder.getAddress());
            context.setUseSmartEta(true);
            context.setDispatchMode("RECALC");
            context.setAvailableOfficers(availableOfficers);

            DispatchContext result = smartDispatchService.calculateSmartDispatch(context);
            List<PoliceOfficer> recommended = result.getRecommendedOfficers();
            if (recommended.isEmpty()) {
                throw new BusinessException(ResultCode.OPERATION_ERROR, "附近暂无可用警力");
            }

            List<Long> policeIds = recommended.stream().map(PoliceOfficer::getId).collect(Collectors.toList());

            String fullRemark = "重算派单：" + (reason != null ? reason : "路况偏航变更")
                    + " | " + result.getDispatchSuggestion();
            DispatchRecord record = createDispatchRecord(alarmId, policeIds, commanderId, fullRemark,
                    alarmOrder.getPriority(), result);
            record.setYawRecalcCount(prev != null && prev.getYawRecalcCount() != null ? prev.getYawRecalcCount() + 1 : 1);
            record.setLastRecalcReason(reason != null ? reason : "路况偏航变更");
            record.setLastRecalcTime(LocalDateTime.now());
            dispatchRecordMapper.updateById(record);

            DispatchTrafficSnapshot snapshot = smartDispatchService.saveTrafficSnapshot(result, record.getId(), record.getDispatchNo());
            if (snapshot != null) {
                record.setTrafficSnapshotId(snapshot.getSnapshotId());
                dispatchRecordMapper.updateById(record);
            }

            for (PoliceOfficer officer : recommended) {
                yawDetectionService.registerDispatchYawCheck(record.getId(), officer.getId(),
                        officer.getLongitude(), officer.getLatitude(), officer.getRoutePolyline());
            }

            sendDispatchNotifications(record, alarmOrder, policeIds, result, recommended);
            log.info("派单重算成功：alarmId={}, 原因={}, 新警力={}", alarmId, reason, policeIds);
            return record;
        } finally {
            redisUtil.unlock(lockKey, lockValue);
        }
    }

    private void sendDispatchNotifications(DispatchRecord record, AlarmOrder alarmOrder,
                                           List<Long> policeIds, DispatchContext ctx,
                                           List<PoliceOfficer> officers) {
        Map<String, Object> wsRecord = buildDispatchWsPayload(record, alarmOrder, ctx, officers);
        mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("dispatch_order", wsRecord));

        for (int i = 0; i < policeIds.size(); i++) {
            Long policeId = policeIds.get(i);
            Map<String, Object> dispatchMsg = new HashMap<>();
            dispatchMsg.put("dispatchId", record.getId());
            dispatchMsg.put("dispatchNo", record.getDispatchNo());
            dispatchMsg.put("alarmId", alarmOrder.getId());
            dispatchMsg.put("alarmNo", alarmOrder.getAlarmNo());
            dispatchMsg.put("alarmType", alarmOrder.getAlarmType());
            dispatchMsg.put("alarmContent", alarmOrder.getContent());
            dispatchMsg.put("address", alarmOrder.getAddress());
            dispatchMsg.put("longitude", alarmOrder.getLongitude());
            dispatchMsg.put("latitude", alarmOrder.getLatitude());
            dispatchMsg.put("priority", record.getPriority());
            dispatchMsg.put("dispatchRemark", record.getDispatchRemark());
            dispatchMsg.put("dispatchTime", record.getDispatchTime());
            dispatchMsg.put("policeId", policeId);

            if (ctx.getOfficerEtaMap() != null) {
                OfficerEtaResultDTO eta = ctx.getOfficerEtaMap().get(policeId);
                if (eta != null) {
                    dispatchMsg.put("eta", eta.getEtaDisplay());
                    dispatchMsg.put("etaSeconds", eta.getEtaSeconds());
                    dispatchMsg.put("routePolyline", eta.getRoutePolyline());
                    dispatchMsg.put("roadDistanceMeters", eta.getRoadDistance());
                    dispatchMsg.put("trafficLevel", eta.getTrafficLevel());
                    dispatchMsg.put("routeName", eta.getRouteName());
                }
            }

            if (i < officers.size()) {
                PoliceOfficer po = officers.get(i);
                dispatchMsg.put("dispatchRank", po.getDispatchRank());
                dispatchMsg.put("dispatchScore", po.getDispatchScore());
            }

            if (ctx.getMultiDispatchPlan() != null && i == 0) {
                dispatchMsg.put("rendezvousLongitude", ctx.getMultiDispatchPlan().getRendezvousLongitude());
                dispatchMsg.put("rendezvousLatitude", ctx.getMultiDispatchPlan().getRendezvousLatitude());
                dispatchMsg.put("rendezvousName", ctx.getMultiDispatchPlan().getRendezvousName());
                dispatchMsg.put("rendezvousEtaSeconds", ctx.getMultiDispatchPlan().getRendezvousToAlarmEta());
                dispatchMsg.put("multiDispatchPlan", ctx.getMultiDispatchPlan());
            }

            Map<String, Object> wsMsg = mqUtil.buildWebSocketMessage("new_dispatch", dispatchMsg);
            wsMsg.put("policeId", policeId);
            mqUtil.sendWebsocketScreenPush(wsMsg);

            Map<String, Object> mobileMsg = new HashMap<>(dispatchMsg);
            mobileMsg.put("type", "new_dispatch");
            mqUtil.sendDispatchNotifyPolice(policeId, mobileMsg);
        }
    }

    private Map<String, Object> buildDispatchWsPayload(DispatchRecord record, AlarmOrder alarmOrder,
                                                       DispatchContext ctx, List<PoliceOfficer> officers) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("dispatchId", record.getId());
        payload.put("dispatchNo", record.getDispatchNo());
        payload.put("alarmId", alarmOrder.getId());
        payload.put("alarmNo", alarmOrder.getAlarmNo());
        payload.put("alarmType", alarmOrder.getAlarmType());
        payload.put("address", alarmOrder.getAddress());
        payload.put("longitude", alarmOrder.getLongitude());
        payload.put("latitude", alarmOrder.getLatitude());
        payload.put("priority", record.getPriority());
        payload.put("dispatchRemark", record.getDispatchRemark());
        payload.put("dispatchTime", record.getDispatchTime());
        payload.put("dispatchStatus", record.getDispatchStatus());
        payload.put("dispatchMode", ctx.getDispatchMode());
        payload.put("dispatchAlgorithm", ctx.getDispatchAlgorithm());
        payload.put("dispatchVersion", ctx.getDispatchVersion());
        payload.put("fastestEtaSeconds", ctx.getFastestEtaSeconds());
        payload.put("fastestPoliceId", ctx.getFastestPoliceId());
        payload.put("avgTrafficLevel", ctx.getAvgTrafficLevel());
        payload.put("trafficSnapshotId", record.getTrafficSnapshotId());
        payload.put("policeIds", record.getPoliceIds());
        payload.put("officers", officers);
        payload.put("officerEtaMap", ctx.getOfficerEtaMap());
        if (ctx.getMultiDispatchPlan() != null) {
            payload.put("multiDispatchPlan", ctx.getMultiDispatchPlan());
        }
        return payload;
    }

    public DispatchRecord getDispatchRecord(Long alarmId) {
        LambdaQueryWrapper<DispatchRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DispatchRecord::getAlarmId, alarmId)
                .orderByDesc(DispatchRecord::getCreateTime)
                .last("LIMIT 1");
        DispatchRecord record = dispatchRecordMapper.selectOne(wrapper);
        if (record != null && record.getPoliceIdsStr() != null) {
            record.setPoliceIds(JSON.parseArray(record.getPoliceIdsStr(), Long.class));
        }
        return record;
    }

    public List<DispatchRecord> getDispatchHistory(Long alarmId) {
        LambdaQueryWrapper<DispatchRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DispatchRecord::getAlarmId, alarmId)
                .orderByDesc(DispatchRecord::getCreateTime);
        List<DispatchRecord> list = dispatchRecordMapper.selectList(wrapper);
        for (DispatchRecord r : list) {
            if (r.getPoliceIdsStr() != null) {
                r.setPoliceIds(JSON.parseArray(r.getPoliceIdsStr(), Long.class));
            }
        }
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public void escalateAlarm(Long alarmId, Long commanderId) {
        AlarmOrder alarmOrder = alarmOrderMapper.selectById(alarmId);
        if (alarmOrder == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "警情不存在");
        }
        alarmOrder.setAlarmStatus(AlarmStatusEnum.ESCALATED.getCode());
        alarmOrderMapper.updateById(alarmOrder);
        saveHandleLog(alarmId, 2, commanderId, "系统",
                String.format("派单超时自动升级，已升级至指挥长处理"));
        log.warn("警情派单超时自动升级，警情ID：{}，升级至指挥长ID：{}", alarmId, commanderId);
    }

    private DispatchRecord createDispatchRecord(Long alarmId, List<Long> policeIds, Long commanderId,
                                                String remark, Integer priority, DispatchContext ctx) {
        DispatchRecord record = new DispatchRecord();
        record.setDispatchNo(generateDispatchNo());
        record.setAlarmId(alarmId);
        record.setPoliceIds(policeIds);
        record.setPoliceIdsStr(JSON.toJSONString(policeIds));
        record.setCommanderId(commanderId);
        record.setDispatchTime(LocalDateTime.now());
        record.setDispatchStatus(0);
        record.setDispatchRemark(remark);
        record.setPriority(priority);

        if (ctx != null) {
            record.setDispatchAlgorithm(ctx.getDispatchAlgorithm());
            record.setDispatchMode(ctx.getDispatchMode());
            record.setDispatchVersion(ctx.getDispatchVersion());
            record.setFastestEtaSeconds(ctx.getFastestEtaSeconds());
            record.setFastestPoliceId(ctx.getFastestPoliceId());
            record.setAvgTrafficLevel(ctx.getAvgTrafficLevel());
            record.setSavedEtaPercent(ctx.getSavedEtaPercent());
            record.setPoliceCount(policeIds != null ? policeIds.size() : 0);
            if (ctx.getMultiDispatchPlan() != null) {
                record.setRendezvousLongitude(ctx.getMultiDispatchPlan().getRendezvousLongitude());
                record.setRendezvousLatitude(ctx.getMultiDispatchPlan().getRendezvousLatitude());
                record.setRendezvousName(ctx.getMultiDispatchPlan().getRendezvousName());
            }
        }
        dispatchRecordMapper.insert(record);

        saveHandleLog(alarmId, 3, UserContext.getUserId(), UserContext.getUsername(),
                String.format("派单成功，派单编号：%s，警力：%s，备注：%s",
                        record.getDispatchNo(), policeIds, remark));
        return record;
    }

    public List<PoliceOfficer> listAvailableOfficers() {
        return getAvailableOfficers();
    }

    public DispatchRecord getDispatchRecordByDispatchId(Long dispatchId) {
        if (dispatchId == null) return null;
        DispatchRecord record = dispatchRecordMapper.selectById(dispatchId);
        if (record != null && record.getPoliceIdsStr() != null) {
            record.setPoliceIds(JSON.parseArray(record.getPoliceIdsStr(), Long.class));
        }
        return record;
    }

    private List<PoliceOfficer> getAvailableOfficers() {
        Set<String> locationKeys = redisUtil.keys(RedisConstant.POLICE_LOCATION_PREFIX + "*");
        Set<String> statusKeys = redisUtil.keys(RedisConstant.POLICE_STATUS_PREFIX + "*");

        Map<Long, Integer> statusMap = new HashMap<>();
        for (String statusKey : statusKeys) {
            try {
                Long officerId = Long.parseLong(statusKey.substring(RedisConstant.POLICE_STATUS_PREFIX.length()));
                String statusStr = redisUtil.get(statusKey);
                Integer status = statusStr != null ? Integer.parseInt(statusStr) : 0;
                statusMap.put(officerId, status);
            } catch (Exception ignored) {
            }
        }

        List<PoliceOfficer> officers = new ArrayList<>();
        for (String locationKey : locationKeys) {
            try {
                Long officerId = Long.parseLong(locationKey.substring(RedisConstant.POLICE_LOCATION_PREFIX.length()));
                String locationStr = redisUtil.get(locationKey);
                if (locationStr != null) {
                    GpsLocation location = JSON.parseObject(locationStr, GpsLocation.class);
                    PoliceOfficer officer = new PoliceOfficer();
                    officer.setId(officerId);
                    officer.setStatus(statusMap.getOrDefault(officerId, 1));
                    officer.setLongitude(location.getLongitude());
                    officer.setLatitude(location.getLatitude());
                    officer.setReportTime(location.getReportTime());
                    officers.add(officer);
                }
            } catch (Exception ignored) {
            }
        }
        return officers;
    }

    private void updateAlarmStatusInternal(AlarmStatusUpdateDTO dto) {
        AlarmOrder alarmOrder = alarmOrderMapper.selectById(dto.getAlarmId());
        AlarmStatusEnum currentStatus = AlarmStatusEnum.getByCode(alarmOrder.getAlarmStatus());
        AlarmStatusEnum nextStatus = AlarmStatusEnum.getByCode(dto.getStatus());

        alarmOrder.setAlarmStatus(dto.getStatus());
        if (AlarmStatusEnum.DISPATCHED.equals(nextStatus)) {
            alarmOrder.setDispatchTime(LocalDateTime.now());
        }
        alarmOrderMapper.updateById(alarmOrder);
    }

    private void sendDispatchTimeoutMessage(Long alarmId) {
        String destination = MqConstant.ALARM_TOPIC + ":" + MqConstant.TAG_DISPATCH;
        rocketMQTemplate.syncSend(destination,
                MessageBuilder.withPayload(String.valueOf(alarmId))
                        .setHeader("delayLevel", 5)
                        .build());
    }

    private String generateDispatchNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long sequence = System.currentTimeMillis() % 1000000;
        return String.format("PD%s%06d", dateStr, sequence);
    }

    private void saveHandleLog(Long alarmId, Integer operateType, Long operatorId,
                               String operatorName, String content) {
        AlarmHandleLog log = new AlarmHandleLog();
        log.setAlarmId(alarmId);
        log.setOperateType(operateType);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setContent(content);
        log.setOperateTime(LocalDateTime.now());
        alarmHandleLogMapper.insert(log);
    }
}
