package com.police.vision.alarm.service;

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
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.enums.AlarmStatusEnum;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.UserContext;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    private final RedisUtil redisUtil;
    private final RocketMQTemplate rocketMQTemplate;

    private static final long DISPATCH_LOCK_EXPIRE = 30;
    private static final long DISPATCH_TIMEOUT_MINUTES = 5;

    @Transactional(rollbackFor = Exception.class)
    public DispatchRecord autoDispatch(Long alarmId) {
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
            context.setLongitude(alarmOrder.getLongitude());
            context.setLatitude(alarmOrder.getLatitude());
            context.setAvailableOfficers(availableOfficers);

            DispatchContext result = dispatchRuleEngine.calculateDispatch(context);
            List<PoliceOfficer> recommendedOfficers = result.getRecommendedOfficers();

            if (recommendedOfficers.isEmpty()) {
                throw new BusinessException(ResultCode.OPERATION_ERROR, "附近暂无可用警力，请人工派单");
            }

            List<Long> policeIds = recommendedOfficers.stream()
                    .map(PoliceOfficer::getId)
                    .collect(Collectors.toList());

            DispatchRecord record = createDispatchRecord(alarmId, policeIds, null,
                    result.getDispatchSuggestion(), result.getPriority());

            AlarmStatusUpdateDTO statusDTO = new AlarmStatusUpdateDTO();
            statusDTO.setAlarmId(alarmId);
            statusDTO.setStatus(AlarmStatusEnum.DISPATCHED.getCode());
            statusDTO.setRemark("自动派单：" + result.getDispatchSuggestion());

            updateAlarmStatusInternal(statusDTO);

            sendDispatchTimeoutMessage(alarmId);

            sendDispatchNotifications(record, alarmOrder, policeIds);

            log.info("自动派单成功，警情ID：{}，派单警力：{}", alarmId, policeIds);
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

        DispatchRecord record = createDispatchRecord(dto.getAlarmId(), dto.getPoliceIds(),
                dto.getCommanderId(), dto.getDispatchRemark(), dto.getPriority());

        AlarmStatusUpdateDTO statusDTO = new AlarmStatusUpdateDTO();
        statusDTO.setAlarmId(dto.getAlarmId());
        statusDTO.setStatus(AlarmStatusEnum.DISPATCHED.getCode());
        statusDTO.setRemark("人工派单：" + dto.getDispatchRemark());

        updateAlarmStatusInternal(statusDTO);

        sendDispatchTimeoutMessage(dto.getAlarmId());

            sendDispatchNotifications(record, alarmOrder, dto.getPoliceIds());

            log.info("人工派单成功，警情ID：{}，派单警力：{}", dto.getAlarmId(), dto.getPoliceIds());
            return record;
        }
    }

    private void sendDispatchNotifications(DispatchRecord record, AlarmOrder alarmOrder, List<Long> policeIds) {
        mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("dispatch_order", record));

        for (Long policeId : policeIds) {
            java.util.Map<String, Object> dispatchMsg = new java.util.HashMap<>();
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

            java.util.Map<String, Object> wsMsg = mqUtil.buildWebSocketMessage("new_dispatch", dispatchMsg);
            wsMsg.put("policeId", policeId);
            mqUtil.sendWebsocketScreenPush(wsMsg);
        }
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
                                                String remark, Integer priority) {
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
        dispatchRecordMapper.insert(record);

        saveHandleLog(alarmId, 3, UserContext.getUserId(), UserContext.getUsername(),
                String.format("派单成功，派单编号：%s，警力：%s，备注：%s",
                        record.getDispatchNo(), policeIds, remark));

        return record;
    }

    private List<PoliceOfficer> getAvailableOfficers() {
        Set<String> locationKeys = redisUtil.keys(RedisConstant.POLICE_LOCATION_PREFIX + "*");
        Set<String> statusKeys = redisUtil.keys(RedisConstant.POLICE_STATUS_PREFIX + "*");

        List<PoliceOfficer> officers = new ArrayList<>();
        for (String statusKey : statusKeys) {
            String officerIdStr = statusKey.substring(RedisConstant.POLICE_STATUS_PREFIX.length());
            Long officerId = Long.parseLong(officerIdStr);
            String statusStr = redisUtil.get(statusKey);
            Integer status = statusStr != null ? Integer.parseInt(statusStr) : 0;

            String locationKey = RedisConstant.POLICE_LOCATION_PREFIX + officerId;
            String locationStr = redisUtil.get(locationKey);

            if (locationStr != null) {
                GpsLocation location = JSON.parseObject(locationStr, GpsLocation.class);
                PoliceOfficer officer = new PoliceOfficer();
                officer.setId(officerId);
                officer.setStatus(status);
                officer.setLongitude(location.getLongitude());
                officer.setLatitude(location.getLatitude());
                officers.add(officer);
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

    private void saveHandleLog(Long alarmId, Integer operateType, Long operatorId, String operatorName, String content) {
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
