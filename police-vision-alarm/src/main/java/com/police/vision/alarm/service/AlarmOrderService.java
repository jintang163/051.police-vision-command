package com.police.vision.alarm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.alarm.entity.AlarmHandleLog;
import com.police.vision.alarm.entity.AlarmOrder;
import com.police.vision.alarm.mapper.AlarmHandleLogMapper;
import com.police.vision.alarm.mapper.AlarmOrderMapper;
import com.police.vision.common.dto.AlarmCreateDTO;
import com.police.vision.common.dto.AlarmStatusUpdateDTO;
import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.enums.AlarmStatusEnum;
import com.police.vision.common.enums.AlarmTypeEnum;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.common.util.UserContext;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmOrderService {

    private final AlarmOrderMapper alarmOrderMapper;
    private final AlarmHandleLogMapper alarmHandleLogMapper;

    @GlobalTransactional(name = "create-alarm", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public AlarmOrder createAlarm(AlarmCreateDTO dto) {
        AlarmTypeEnum typeEnum = AlarmTypeEnum.getByCode(dto.getAlarmType());

        AlarmOrder alarmOrder = new AlarmOrder();
        BeanUtils.copyProperties(dto, alarmOrder);
        alarmOrder.setAlarmNo(generateAlarmNo());
        alarmOrder.setAlarmStatus(AlarmStatusEnum.PENDING.getCode());
        alarmOrder.setPriority(typeEnum.getPriority());
        alarmOrderMapper.insert(alarmOrder);

        saveHandleLog(alarmOrder.getId(), 0, UserContext.getUserId(), UserContext.getUsername(),
                "创建警情工单，警情编号：" + alarmOrder.getAlarmNo());

        log.info("创建警情工单成功，ID：{}，警情编号：{}", alarmOrder.getId(), alarmOrder.getAlarmNo());
        return alarmOrder;
    }

    public PageResult<AlarmOrder> getAlarmList(PageParam pageParam, Integer status, Integer type) {
        LambdaQueryWrapper<AlarmOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(AlarmOrder::getAlarmStatus, status);
        }
        if (type != null) {
            wrapper.eq(AlarmOrder::getAlarmType, type);
        }
        wrapper.orderByDesc(AlarmOrder::getCreateTime);

        Page<AlarmOrder> page = new Page<>(pageParam.getPageNum(), pageParam.getPageSize());
        IPage<AlarmOrder> result = alarmOrderMapper.selectPage(page, wrapper);

        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    public AlarmOrder getAlarmDetail(Long id) {
        AlarmOrder alarmOrder = alarmOrderMapper.selectById(id);
        if (alarmOrder == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return alarmOrder;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAlarmStatus(AlarmStatusUpdateDTO dto) {
        AlarmOrder alarmOrder = getAlarmDetail(dto.getAlarmId());
        AlarmStatusEnum currentStatus = AlarmStatusEnum.getByCode(alarmOrder.getAlarmStatus());
        AlarmStatusEnum nextStatus = AlarmStatusEnum.getByCode(dto.getStatus());

        if (currentStatus == null || nextStatus == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态值无效");
        }

        if (!currentStatus.canTransitionTo(nextStatus)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    String.format("无法从状态[%s]转换到[%s]", currentStatus.getName(), nextStatus.getName()));
        }

        alarmOrder.setAlarmStatus(dto.getStatus());
        if (dto.getLongitude() != null) {
            alarmOrder.setLongitude(dto.getLongitude());
        }
        if (dto.getLatitude() != null) {
            alarmOrder.setLatitude(dto.getLatitude());
        }

        LocalDateTime now = LocalDateTime.now();
        if (AlarmStatusEnum.DISPATCHED.equals(nextStatus)) {
            alarmOrder.setDispatchTime(now);
        } else if (AlarmStatusEnum.ARRIVED.equals(nextStatus)) {
            alarmOrder.setArriveTime(now);
        } else if (AlarmStatusEnum.COMPLETED.equals(nextStatus)) {
            alarmOrder.setFinishTime(now);
            alarmOrder.setHandleResult(dto.getHandleResult());
        }
        alarmOrderMapper.updateById(alarmOrder);

        saveHandleLog(dto.getAlarmId(), 1, UserContext.getUserId(), UserContext.getUsername(),
                String.format("更新警情状态：%s -> %s，备注：%s",
                        currentStatus.getName(), nextStatus.getName(), dto.getRemark()));

        log.info("更新警情状态成功，警情ID：{}，状态：{} -> {}", dto.getAlarmId(), currentStatus.getName(), nextStatus.getName());
    }

    private String generateAlarmNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long sequence = SnowflakeIdUtil.nextId() % 1000000;
        return String.format("BJ%s%06d", dateStr, sequence);
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
