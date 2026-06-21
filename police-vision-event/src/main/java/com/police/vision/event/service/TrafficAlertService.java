package com.police.vision.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.event.entity.SecTrafficAlert;
import com.police.vision.event.mapper.SecTrafficAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficAlertService {

    private final SecTrafficAlertMapper secTrafficAlertMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;

    @Transactional(rollbackFor = Exception.class)
    public Result<Long> createAlert(Long eventId, String alertType, Integer alertLevel,
                                    String location, Double lng, Double lat,
                                    Long countValue, Long thresholdValue) {
        log.info("创建交通预警开始，eventId={}, alertType={}, alertLevel={}", eventId, alertType, alertLevel);
        try {
            SecTrafficAlert alert = new SecTrafficAlert();
            alert.setEventId(eventId);
            alert.setAlertType(alertType);
            alert.setAlertLevel(alertLevel);
            alert.setLocation(location);
            alert.setLng(lng);
            alert.setLat(lat);
            alert.setCountValue(countValue);
            alert.setThresholdValue(thresholdValue);
            alert.setAlertTime(LocalDateTime.now());
            alert.setHandled(0);
            secTrafficAlertMapper.insert(alert);

            Map<String, Object> wsMessage = mqUtil.buildWebSocketMessage("traffic_alert", alert);
            mqUtil.sendWebsocketScreenPush(wsMessage);

            log.info("创建交通预警成功，alertId={}, eventId={}", alert.getId(), eventId);
            return Result.success(alert.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建交通预警失败，eventId={}", eventId, e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "创建交通预警失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> handleAlert(Long alertId, String handleRemark) {
        log.info("处理交通预警开始，alertId={}", alertId);
        SecTrafficAlert alert = secTrafficAlertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "预警记录不存在");
        }
        try {
            alert.setHandled(1);
            alert.setHandleRemark(handleRemark);
            secTrafficAlertMapper.updateById(alert);

            Map<String, Object> wsMessage = mqUtil.buildWebSocketMessage("traffic_alert_handled", alert);
            mqUtil.sendWebsocketScreenPush(wsMessage);

            log.info("处理交通预警成功，alertId={}", alertId);
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("处理交通预警失败，alertId={}", alertId, e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "处理交通预警失败：" + e.getMessage());
        }
    }

    public Result<PageResult<SecTrafficAlert>> listAlerts(Long eventId, String alertType,
                                                          Integer handled, Integer alertLevel,
                                                          int page, int size) {
        log.debug("分页查询预警列表，eventId={}, alertType={}, handled={}, alertLevel={}, page={}, size={}",
                eventId, alertType, handled, alertLevel, page, size);
        LambdaQueryWrapper<SecTrafficAlert> wrapper = new LambdaQueryWrapper<>();
        if (eventId != null) {
            wrapper.eq(SecTrafficAlert::getEventId, eventId);
        }
        if (StringUtils.hasText(alertType)) {
            wrapper.eq(SecTrafficAlert::getAlertType, alertType);
        }
        if (handled != null) {
            wrapper.eq(SecTrafficAlert::getHandled, handled);
        }
        if (alertLevel != null) {
            wrapper.eq(SecTrafficAlert::getAlertLevel, alertLevel);
        }
        wrapper.orderByDesc(SecTrafficAlert::getAlertTime);
        Page<SecTrafficAlert> pageParam = new Page<>(page, size);
        IPage<SecTrafficAlert> result = secTrafficAlertMapper.selectPage(pageParam, wrapper);
        PageResult<SecTrafficAlert> pageResult = PageResult.of(result.getTotal(), result.getRecords(), page, size);
        return Result.success(pageResult);
    }

    public Result<Map<String, Object>> getAlertStats(Long eventId) {
        log.debug("统计预警数据，eventId={}", eventId);
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        LambdaQueryWrapper<SecTrafficAlert> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.eq(SecTrafficAlert::getEventId, eventId);
        todayWrapper.between(SecTrafficAlert::getAlertTime, startOfDay, endOfDay);
        Long todayCount = secTrafficAlertMapper.selectCount(todayWrapper);

        LambdaQueryWrapper<SecTrafficAlert> unhandledWrapper = new LambdaQueryWrapper<>();
        unhandledWrapper.eq(SecTrafficAlert::getEventId, eventId);
        unhandledWrapper.eq(SecTrafficAlert::getHandled, 0);
        Long unhandledCount = secTrafficAlertMapper.selectCount(unhandledWrapper);

        Map<Integer, Long> levelStats = new HashMap<>();
        for (int level = 1; level <= 3; level++) {
            LambdaQueryWrapper<SecTrafficAlert> levelWrapper = new LambdaQueryWrapper<>();
            levelWrapper.eq(SecTrafficAlert::getEventId, eventId);
            levelWrapper.eq(SecTrafficAlert::getAlertLevel, level);
            levelWrapper.between(SecTrafficAlert::getAlertTime, startOfDay, endOfDay);
            Long count = secTrafficAlertMapper.selectCount(levelWrapper);
            levelStats.put(level, count);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("todayCount", todayCount);
        stats.put("unhandledCount", unhandledCount);
        stats.put("levelStats", levelStats);

        return Result.success(stats);
    }
}
