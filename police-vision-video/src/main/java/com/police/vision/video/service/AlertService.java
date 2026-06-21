package com.police.vision.video.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.video.entity.AlertRecord;
import com.police.vision.video.mapper.AlertRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRecordMapper alertRecordMapper;

    public AlertRecord getById(Long id) {
        return alertRecordMapper.selectById(id);
    }

    public AlertRecord getByAlertId(String alertId) {
        return alertRecordMapper.selectByAlertId(alertId);
    }

    public PageResult<AlertRecord> getAlertList(PageParam param, Integer alertType, Integer alertLevel,
                                                Integer processed, String cameraId,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        Page<AlertRecord> page = new Page<>(param.getPageNum(), param.getPageSize());
        Page<AlertRecord> result = alertRecordMapper.selectAlertPage(
                page, param, alertType, alertLevel, processed, cameraId, startTime, endTime);
        return PageResult.of(result.getTotal(), result.getRecords(), param.getPageNum(), param.getPageSize());
    }

    public List<AlertRecord> getByCameraId(String cameraId, Integer limit) {
        return alertRecordMapper.selectByCameraId(cameraId, limit);
    }

    @Transactional(rollbackFor = Exception.class)
    public void processAlert(Long alertId, Long userId, String result) {
        AlertRecord alert = alertRecordMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "告警记录不存在");
        }
        if (alert.getProcessed() != null && alert.getProcessed() == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "告警已处理");
        }
        alert.setProcessed(1);
        alert.setProcessorId(userId);
        alert.setProcessTime(LocalDateTime.now());
        alert.setProcessResult(result);
        alertRecordMapper.updateById(alert);
        log.info("告警处理完成：alertId={}, userId={}, result={}", alert.getAlertId(), userId, result);
    }

    public Map<String, Object> getAlertStats(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        Map<String, Object> stats = new HashMap<>();
        List<Map<String, Object>> typeStats = alertRecordMapper.countByType(startTime, endTime);
        List<Map<String, Object>> levelStats = alertRecordMapper.countByLevel(startTime, endTime);
        List<Map<String, Object>> processedStats = alertRecordMapper.countByProcessed(startTime, endTime);
        long total = 0;
        long processed = 0;
        long unprocessed = 0;
        for (Map<String, Object> typeStat : typeStats) {
            total += ((Number) typeStat.get("count")).longValue();
        }
        for (Map<String, Object> processedStat : processedStats) {
            Integer status = (Integer) processedStat.get("processed");
            long count = ((Number) processedStat.get("count")).longValue();
            if (status != null && status == 1) {
                processed = count;
            } else {
                unprocessed = count;
            }
        }
        stats.put("total", total);
        stats.put("processed", processed);
        stats.put("unprocessed", unprocessed);
        stats.put("processRate", total > 0 ? (double) processed / total : 0);
        stats.put("byType", typeStats);
        stats.put("byLevel", levelStats);
        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        log.debug("告警统计：{}", stats);
        return stats;
    }

    public Map<String, Object> getRealtimeStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime hourStart = now.minusHours(1);
        Map<String, Object> stats = new HashMap<>();
        stats.put("today", getAlertStats(todayStart, now));
        stats.put("lastHour", getAlertStats(hourStart, now));
        return stats;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveAlert(AlertRecord alert) {
        alertRecordMapper.insert(alert);
    }
}
