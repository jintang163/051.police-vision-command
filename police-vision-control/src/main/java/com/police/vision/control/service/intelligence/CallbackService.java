package com.police.vision.control.service.intelligence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.police.vision.common.result.Result;
import com.police.vision.control.dto.intelligence.CallbackManualExecuteDTO;
import com.police.vision.control.dto.intelligence.CallbackStatisticsDTO;
import com.police.vision.control.dto.intelligence.CallbackTaskCreateDTO;
import com.police.vision.control.dto.intelligence.CallbackTaskQueryDTO;
import com.police.vision.control.entity.intelligence.CallbackResult;
import com.police.vision.control.entity.intelligence.CallbackTask;
import com.police.vision.control.entity.intelligence.CallbackTemplate;
import com.police.vision.control.mapper.intelligence.CallbackResultMapper;
import com.police.vision.control.mapper.intelligence.CallbackTaskMapper;
import com.police.vision.control.mapper.intelligence.CallbackTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService extends ServiceImpl<CallbackTaskMapper, CallbackTask> {

    private final CallbackResultMapper callbackResultMapper;
    private final CallbackTemplateMapper callbackTemplateMapper;
    private final CallbackDispatchService callbackDispatchService;

    @Transactional(rollbackFor = Exception.class)
    public Result<CallbackTask> createTask(CallbackTaskCreateDTO dto) {
        if (dto == null) {
            return Result.fail("创建任务参数不能为空");
        }
        if (!StringUtils.hasText(dto.getReporterPhone())) {
            return Result.fail("回访号码不能为空");
        }
        try {
            CallbackTask task = callbackDispatchService.createCallbackTask(dto);
            log.info("创建警情回访任务成功，taskId: {}", task.getCallbackTaskId());
            return Result.success(task);
        } catch (Exception e) {
            log.error("创建警情回访任务失败", e);
            return Result.fail("创建任务失败: " + e.getMessage());
        }
    }

    public Result<IPage<CallbackTask>> pageQuery(CallbackTaskQueryDTO dto) {
        if (dto == null) {
            dto = new CallbackTaskQueryDTO();
        }
        int pageNum = dto.getPageNum() != null ? dto.getPageNum() : 1;
        int pageSize = dto.getPageSize() != null ? dto.getPageSize() : 20;

        LambdaQueryWrapper<CallbackTask> wrapper = new LambdaQueryWrapper<>();

        if (dto.getTaskStatus() != null) {
            wrapper.eq(CallbackTask::getTaskStatus, dto.getTaskStatus());
        }
        if (dto.getSourceType() != null) {
            wrapper.eq(CallbackTask::getSourceType, dto.getSourceType());
        }
        if (dto.getTransferHumanFlag() != null) {
            wrapper.eq(CallbackTask::getTransferHumanFlag, dto.getTransferHumanFlag());
        }
        if (StringUtils.hasText(dto.getAreaCode())) {
            wrapper.eq(CallbackTask::getAreaCode, dto.getAreaCode());
        }
        if (StringUtils.hasText(dto.getAlertDeptCode())) {
            wrapper.eq(CallbackTask::getAlertDeptCode, dto.getAlertDeptCode());
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            wrapper.between(CallbackTask::getScheduledTime, dto.getStartTime(), dto.getEndTime());
        } else if (dto.getStartTime() != null) {
            wrapper.ge(CallbackTask::getScheduledTime, dto.getStartTime());
        } else if (dto.getEndTime() != null) {
            wrapper.le(CallbackTask::getScheduledTime, dto.getEndTime());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String keyword = dto.getKeyword();
            wrapper.and(w -> w
                    .like(CallbackTask::getReporterName, keyword)
                    .or().like(CallbackTask::getCallbackTaskNo, keyword)
                    .or().like(CallbackTask::getSourceNo, keyword)
            );
        }

        wrapper.orderByDesc(CallbackTask::getScheduledTime);

        Page<CallbackTask> page = new Page<>(pageNum, pageSize);
        IPage<CallbackTask> result = baseMapper.selectPage(page, wrapper);
        return Result.success(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> manualExecute(CallbackManualExecuteDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTaskId())) {
            return Result.fail("任务ID不能为空");
        }
        try {
            CallbackTask task = getByTaskId(dto.getTaskId());
            if (task == null) {
                return Result.fail("回访任务不存在");
            }
            Integer status = task.getTaskStatus();
            if (status == null || (status != 0 && status != 3)) {
                return Result.fail("当前任务状态不允许手动执行，status: " + status);
            }
            boolean success = callbackDispatchService.dispatchSingleTask(task);
            if (success) {
                return Result.success(true);
            } else {
                return Result.fail("手动发起回访失败");
            }
        } catch (Exception e) {
            log.error("手动执行回访任务异常，taskId: {}", dto.getTaskId(), e);
            return Result.fail("手动执行失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> retryTask(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Result.fail("任务ID不能为空");
        }
        try {
            CallbackTask task = getByTaskId(taskId);
            if (task == null) {
                return Result.fail("回访任务不存在");
            }
            task.setTaskStatus(0);
            task.setTaskStatusName("待发起");
            task.setCallTimes(0);
            task.setCallResult(null);
            task.setCallResultMsg(null);
            task.setNextAttemptTime(null);
            baseMapper.updateById(task);

            boolean success = callbackDispatchService.dispatchSingleTask(task);
            if (success) {
                log.info("重置并重试任务成功，taskId: {}", taskId);
                return Result.success(true);
            } else {
                return Result.fail("重试发起回访失败");
            }
        } catch (Exception e) {
            log.error("重试任务异常，taskId: {}", taskId, e);
            return Result.fail("重试任务失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> manualTransfer(String taskId, Long officerId, String officerName) {
        if (!StringUtils.hasText(taskId)) {
            return Result.fail("任务ID不能为空");
        }
        try {
            CallbackTask task = getByTaskId(taskId);
            if (task == null) {
                return Result.fail("回访任务不存在");
            }
            task.setTransferHumanFlag(1);
            task.setTaskStatus(4);
            task.setTaskStatusName("需人工回访");
            task.setHumanOfficerId(officerId);
            task.setHumanOfficerName(officerName);
            task.setTransferHumanTime(LocalDateTime.now());
            baseMapper.updateById(task);
            log.info("任务转人工成功，taskId: {}, officer: {}", taskId, officerName);
            return Result.success(true);
        } catch (Exception e) {
            log.error("任务转人工异常，taskId: {}", taskId, e);
            return Result.fail("转人工失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> manualFinish(String taskId, Long reviewerId, String reviewerName,
                                         Integer timelinessScore, Integer attitudeScore, Integer solvingScore,
                                         Integer satisfactionLevel, String suggestionText, String summaryText) {
        if (!StringUtils.hasText(taskId)) {
            return Result.fail("任务ID不能为空");
        }
        try {
            CallbackTask task = getByTaskId(taskId);
            if (task == null) {
                return Result.fail("回访任务不存在");
            }

            CallbackResult resultData = new CallbackResult();
            resultData.setCallbackResultId(java.util.UUID.randomUUID().toString().replace("-", ""));
            resultData.setCallbackTaskId(task.getCallbackTaskId());
            resultData.setCallbackTaskNo(task.getCallbackTaskNo());
            resultData.setCallId(task.getCallId());
            resultData.setCallDuration(task.getCallDuration());
            if (timelinessScore != null) resultData.setTimelinessScore(timelinessScore);
            if (attitudeScore != null) resultData.setAttitudeScore(attitudeScore);
            if (solvingScore != null) resultData.setSolvingScore(solvingScore);
            if (timelinessScore != null && attitudeScore != null && solvingScore != null) {
                resultData.setOverallScore((int) Math.round((timelinessScore + attitudeScore + solvingScore) / 3.0));
            }
            if (satisfactionLevel != null) {
                resultData.setSatisfactionLevel(satisfactionLevel);
                resultData.setSatisfactionLevelName(mapSatisfactionName(satisfactionLevel));
            }
            resultData.setSuggestionText(suggestionText);
            resultData.setSummaryText(summaryText);
            resultData.setReviewerId(reviewerId);
            resultData.setReviewerName(reviewerName);
            resultData.setReviewStatus(1);
            resultData.setReviewStatusName("已通过");
            resultData.setReviewTime(LocalDateTime.now());

            CallbackResult existResult = callbackResultMapper.selectByTaskId(taskId);
            if (existResult != null) {
                resultData.setId(existResult.getId());
                callbackResultMapper.updateById(resultData);
            } else {
                callbackResultMapper.insert(resultData);
            }

            task.setTaskStatus(2);
            task.setTaskStatusName("已完成");
            task.setHumanFinishTime(LocalDateTime.now());
            baseMapper.updateById(task);

            log.info("人工完成回访任务成功，taskId: {}, reviewer: {}", taskId, reviewerName);
            return Result.success(true);
        } catch (Exception e) {
            log.error("人工完成回访异常，taskId: {}", taskId, e);
            return Result.fail("人工完成失败: " + e.getMessage());
        }
    }

    public Result<Map<String, Object>> getTaskDetail(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Result.fail("任务ID不能为空");
        }
        try {
            CallbackTask task = getByTaskId(taskId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("task", task);
            if (task != null) {
                CallbackResult callbackResult = callbackResultMapper.selectByTaskId(task.getCallbackTaskId());
                result.put("result", callbackResult);
            } else {
                result.put("result", null);
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取任务详情异常，taskId: {}", taskId, e);
            return Result.fail("获取详情失败: " + e.getMessage());
        }
    }

    public Result<List<CallbackTemplate>> listTemplates() {
        try {
            List<CallbackTemplate> templates = callbackTemplateMapper.selectEnabledTemplates();
            return Result.success(templates);
        } catch (Exception e) {
            log.error("获取回访模板列表异常", e);
            return Result.fail("获取模板失败: " + e.getMessage());
        }
    }

    public Result<Map<String, Object>> getStatistics(CallbackStatisticsDTO dto) {
        try {
            Map<String, Object> statistics = callbackDispatchService.getStatistics(dto);
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取回访统计异常", e);
            return Result.fail("获取统计失败: " + e.getMessage());
        }
    }

    private CallbackTask getByTaskId(String taskId) {
        LambdaQueryWrapper<CallbackTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallbackTask::getCallbackTaskId, taskId);
        return baseMapper.selectOne(wrapper);
    }

    private String mapSatisfactionName(Integer level) {
        if (level == null) return "一般";
        switch (level) {
            case 1: return "非常满意";
            case 2: return "满意";
            case 3: return "一般";
            case 4: return "不满意";
            case 5: return "非常不满意";
            default: return "一般";
        }
    }
}
