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
import com.police.vision.event.dto.TrafficMonitorDTO;
import com.police.vision.event.entity.SecEvent;
import com.police.vision.event.entity.SecTrafficAlert;
import com.police.vision.event.mapper.SecEventMapper;
import com.police.vision.event.mapper.SecTrafficAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private final SecEventMapper secEventMapper;
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

    public Result<String> startTrafficMonitor(TrafficMonitorDTO dto) {
        log.info("启动交通监控任务，eventId={}", dto.getEventId());
        try {
            SecEvent event = secEventMapper.selectById(dto.getEventId());
            if (event == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "活动不存在");
            }
            if (event.getStatus() == null || event.getStatus() != 1) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "活动状态不是进行中，无法启动监控");
            }

            String monitorId = UUID.randomUUID().toString().replace("-", "");
            String statusKey = MqConstant.EVENT_MONITOR_STATUS_KEY + dto.getEventId();

            Map<String, Object> monitorInfo = new HashMap<>();
            monitorInfo.put("monitorId", monitorId);
            monitorInfo.put("eventId", dto.getEventId());
            monitorInfo.put("status", "running");
            monitorInfo.put("pedestrianThreshold", dto.getPedestrianThreshold());
            monitorInfo.put("vehicleThreshold", dto.getVehicleThreshold());
            monitorInfo.put("windowSeconds", dto.getWindowSeconds() != null ? dto.getWindowSeconds() : 60);
            monitorInfo.put("startTime", System.currentTimeMillis());

            redisUtil.setObject(statusKey, monitorInfo, 24, TimeUnit.HOURS);

            submitFlinkJobAsync(dto, monitorId);

            log.info("交通监控任务启动成功，monitorId={}, eventId={}", monitorId, dto.getEventId());
            return Result.success(monitorId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("启动交通监控任务失败，eventId={}", dto.getEventId(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "启动监控失败：" + e.getMessage());
        }
    }

    @Async
    public void submitFlinkJobAsync(TrafficMonitorDTO dto, String monitorId) {
        try {
            log.info("异步提交Flink任务：monitorId={}, eventId={}", monitorId, dto.getEventId());
            String flinkCommand = buildFlinkSubmitCommand(dto);
            log.info("Flink提交命令：{}", flinkCommand);
            log.info("Flink任务已提交（模拟），实际部署时请通过命令行或Flink REST API提交");
        } catch (Exception e) {
            log.error("异步提交Flink任务失败：monitorId={}", monitorId, e);
        }
    }

    private String buildFlinkSubmitCommand(TrafficMonitorDTO dto) {
        Long pedestrianThreshold = dto.getPedestrianThreshold() != null ? dto.getPedestrianThreshold() : 100L;
        Long vehicleThreshold = dto.getVehicleThreshold() != null ? dto.getVehicleThreshold() : 50L;
        int windowSeconds = dto.getWindowSeconds() != null ? dto.getWindowSeconds() : 60;

        return String.format(
                "flink run -c com.police.vision.flink.job.EventTrafficMonitorJob police-vision-flink.jar %d %d %d %d",
                dto.getEventId(), pedestrianThreshold, vehicleThreshold, windowSeconds
        );
    }

    public Result<Void> stopTrafficMonitor(Long eventId) {
        log.info("停止交通监控任务，eventId={}", eventId);
        try {
            String statusKey = MqConstant.EVENT_MONITOR_STATUS_KEY + eventId;
            String status = redisUtil.get(statusKey);
            if (status == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "监控任务不存在");
            }

            Map<String, Object> monitorInfo = redisUtil.getObject(statusKey, Map.class);
            if (monitorInfo != null) {
                monitorInfo.put("status", "stopped");
                monitorInfo.put("stopTime", System.currentTimeMillis());
                redisUtil.setObject(statusKey, monitorInfo, 24, TimeUnit.HOURS);
            }

            log.info("交通监控任务已停止，eventId={}", eventId);
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("停止交通监控任务失败，eventId={}", eventId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "停止监控失败：" + e.getMessage());
        }
    }

    public Result<Map<String, Object>> getMonitorStatus(Long eventId) {
        log.debug("获取监控状态，eventId={}", eventId);
        try {
            String statusKey = MqConstant.EVENT_MONITOR_STATUS_KEY + eventId;
            Map<String, Object> monitorInfo = redisUtil.getObject(statusKey, Map.class);
            if (monitorInfo == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("eventId", eventId);
                result.put("status", "stopped");
                return Result.success(result);
            }
            return Result.success(monitorInfo);
        } catch (Exception e) {
            log.error("获取监控状态失败，eventId={}", eventId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取监控状态失败：" + e.getMessage());
        }
    }
}
