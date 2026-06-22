package com.police.vision.control.service.intelligence;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.config.intelligence.AliyunDyvmsConfig;
import com.police.vision.control.config.intelligence.CallbackConfig;
import com.police.vision.control.dto.intelligence.CallbackStatisticsDTO;
import com.police.vision.control.dto.intelligence.CallbackTaskCreateDTO;
import com.police.vision.control.dto.intelligence.SentimentAnalysisDTO;
import com.police.vision.control.dto.intelligence.SentimentResultDTO;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackDispatchService {

    private final CallbackTaskMapper callbackTaskMapper;
    private final CallbackResultMapper callbackResultMapper;
    private final CallbackTemplateMapper callbackTemplateMapper;
    private final AliyunDyvmsService aliyunDyvmsService;
    private final DeepSeekService deepSeekService;
    private final CallbackConfig callbackConfig;
    private final AliyunDyvmsConfig aliyunDyvmsConfig;

    @Transactional(rollbackFor = Exception.class)
    public CallbackTask createCallbackTask(CallbackTaskCreateDTO dto) {
        CallbackTask task = new CallbackTask();

        task.setCallbackTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setCallbackTaskNo(generateTaskNo());

        if (StringUtils.hasText(dto.getTemplateId())) {
            task.setTemplateId(dto.getTemplateId());
        } else {
            CallbackTemplate defaultTemplate = callbackTemplateMapper.selectDefaultTemplate();
            if (defaultTemplate != null) {
                task.setTemplateId(defaultTemplate.getTemplateId());
                task.setTemplateName(defaultTemplate.getTemplateName());
            }
        }

        task.setSourceType(dto.getSourceType() != null ? dto.getSourceType() : 1);
        task.setSourceTypeName(mapSourceTypeName(task.getSourceType()));
        task.setSourceId(dto.getSourceId());
        task.setSourceNo(dto.getSourceNo());
        task.setAlertDeptCode(dto.getAlertDeptCode());
        task.setAlertDeptName(dto.getAlertDeptName());
        task.setAreaCode(dto.getAreaCode());
        task.setAreaName(dto.getAreaName());
        task.setCaseType(dto.getCaseType());
        task.setCaseTypeName(dto.getCaseTypeName());
        task.setBriefDescription(dto.getBriefDescription());
        task.setAlertOfficerId(dto.getAlertOfficerId());
        task.setAlertOfficerName(dto.getAlertOfficerName());
        task.setCloseTime(dto.getCloseTime());
        task.setReporterName(dto.getReporterName());
        task.setReporterPhone(dto.getReporterPhone());

        LocalDateTime closeTime = dto.getCloseTime() != null ? dto.getCloseTime() : LocalDateTime.now();
        int delayHours = callbackConfig.getDefaultAutoCallbackDelayHours() != null
                ? callbackConfig.getDefaultAutoCallbackDelayHours() : 24;
        LocalDateTime scheduledTime = closeTime.plusHours(delayHours);
        task.setScheduledTime(adjustToWorkingHours(scheduledTime));

        task.setTaskStatus(0);
        task.setTaskStatusName("待发起");
        task.setCallTimes(0);
        task.setTransferHumanFlag(0);
        task.setMaxRetryTimes(callbackConfig.getMaxRetryTimes() != null ? callbackConfig.getMaxRetryTimes() : 3);

        callbackTaskMapper.insert(task);
        log.info("创建警情回访任务成功，taskId: {}, taskNo: {}, phone: {}",
                task.getCallbackTaskId(), task.getCallbackTaskNo(), task.getReporterPhone());
        return task;
    }

    public int scanAndDispatchTasks(int limit) {
        int delayHours = callbackConfig.getDefaultAutoCallbackDelayHours() != null
                ? callbackConfig.getDefaultAutoCallbackDelayHours() : 24;
        List<CallbackTask> tasks = callbackTaskMapper.selectScheduledTasks(delayHours, limit);
        if (tasks == null || tasks.isEmpty()) {
            log.info("扫描待回访任务：无待处理任务");
            return 0;
        }

        int successCount = 0;
        for (CallbackTask task : tasks) {
            try {
                if (!isWithinWorkingHours(LocalDateTime.now())) {
                    log.info("当前非工作时段，任务{}挪到次日，taskId: {}",
                            task.getCallbackTaskNo(), task.getCallbackTaskId());
                    if (task.getScheduledTime() != null) {
                        task.setScheduledTime(adjustToWorkingHours(task.getScheduledTime().plusDays(1)));
                        callbackTaskMapper.updateById(task);
                    }
                    continue;
                }
                boolean dispatched = dispatchSingleTask(task);
                if (dispatched) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("调度回访任务异常，taskId: {}", task.getCallbackTaskId(), e);
            }
        }
        log.info("扫描待回访任务完成，总数: {}, 成功发起: {}", tasks.size(), successCount);
        return successCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean dispatchSingleTask(CallbackTask task) {
        if (task == null) {
            return false;
        }
        Integer status = task.getTaskStatus();
        Integer callTimes = task.getCallTimes() != null ? task.getCallTimes() : 0;
        Integer maxRetry = task.getMaxRetryTimes() != null ? task.getMaxRetryTimes() : 3;

        boolean canDispatch = (status != null && status == 0)
                || (status != null && status == 3 && callTimes < maxRetry);
        if (!canDispatch) {
            log.warn("任务状态不允许发起，taskId: {}, status: {}, callTimes: {}",
                    task.getCallbackTaskId(), status, callTimes);
            return false;
        }

        CallbackTemplate template = selectTemplateByTemplateId(task.getTemplateId());
        if (template == null) {
            template = callbackTemplateMapper.selectDefaultTemplate();
        }
        if (template == null) {
            log.error("未找到回访模板，taskId: {}", task.getCallbackTaskId());
            return false;
        }
        task.setTemplateName(template.getTemplateName());

        task.setTaskStatus(1);
        task.setTaskStatusName("呼叫中");
        task.setCallStartTime(LocalDateTime.now());
        task.setCallTimes(callTimes + 1);
        task.setLastAttemptTime(LocalDateTime.now());

        Map<String, String> ttsParam = buildTtsParam(task, template);
        String ttsCode = StringUtils.hasText(template.getTtsCode())
                ? template.getTtsCode() : aliyunDyvmsConfig.getTtsCode();
        String callId = aliyunDyvmsService.singleCallByTts(
                task.getReporterPhone(),
                aliyunDyvmsConfig.getCalledShowNumber(),
                ttsCode,
                ttsParam
        );

        if (!StringUtils.hasText(callId)) {
            log.error("语音外呼失败，taskId: {}, phone: {}", task.getCallbackTaskId(), task.getReporterPhone());
            task.setTaskStatus(3);
            task.setTaskStatusName("呼叫失败");
            task.setCallResultMsg("外呼接口调用失败");
            callbackTaskMapper.updateById(task);
            return false;
        }

        task.setCallId(callId);
        callbackTaskMapper.updateById(task);
        log.info("回访任务发起成功，taskId: {}, callId: {}, phone: {}, 第{}次尝试",
                task.getCallbackTaskId(), callId, task.getReporterPhone(), task.getCallTimes());
        return true;
    }

    public Map<String, String> buildTtsParam(CallbackTask task, CallbackTemplate template) {
        Map<String, String> param = new LinkedHashMap<>();
        param.put("name", task.getReporterName() != null ? maskName(task.getReporterName()) : "先生/女士");
        param.put("caseTypeName", task.getCaseTypeName() != null ? task.getCaseTypeName() : "警情");
        if (task.getCloseTime() != null) {
            param.put("closeTimeStr", task.getCloseTime().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时")));
        } else {
            param.put("closeTimeStr", "近期");
        }
        param.put("alertDeptName", task.getAlertDeptName() != null ? task.getAlertDeptName() : "辖区派出所");
        param.put("taskNo", task.getCallbackTaskNo() != null ? task.getCallbackTaskNo() : "");
        return param;
    }

    public int pollAndUpdateCallStatus() {
        int interval = aliyunDyvmsConfig.getPollingInterval() != null ? aliyunDyvmsConfig.getPollingInterval() : 5;
        List<CallbackTask> callingTasks = callbackTaskMapper.selectCallingTimeoutTasks(interval);
        if (callingTasks == null || callingTasks.isEmpty()) {
            return 0;
        }

        int handled = 0;
        for (CallbackTask task : callingTasks) {
            try {
                if (!StringUtils.hasText(task.getCallId())) {
                    continue;
                }
                Map<String, Object> detail = aliyunDyvmsService.queryCallDetail(task.getCallId());
                String status = detail != null ? String.valueOf(detail.get("status")) : null;

                if ("200005".equals(status)) {
                    handleCallFinished(task, detail);
                    handled++;
                } else if ("200003".equals(status) || "200004".equals(status)) {
                    handleCallFailed(task, detail);
                    handled++;
                } else if ("200001".equals(status) || "200002".equals(status)) {
                    log.debug("通话进行中，taskId: {}, status: {}", task.getCallbackTaskId(), status);
                }
            } catch (Exception e) {
                log.error("轮询通话状态异常，taskId: {}", task.getCallbackTaskId(), e);
            }
        }
        if (handled > 0) {
            log.info("轮询呼叫状态完成，处理{}个任务", handled);
        }
        return handled;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleCallFinished(CallbackTask task, Map<String, Object> detail) {
        if (detail != null) {
            task.setCallEndTime(parseDateTime(detail.get("endTime")));
            Object duration = detail.get("duration");
            if (duration != null) {
                try {
                    task.setCallDuration(Integer.parseInt(String.valueOf(duration)));
                } catch (NumberFormatException ignored) {
                }
            }
            task.setCallResult("200005");
            task.setCallResultMsg(String.valueOf(detail.get("statusDesc")));
        }
        task.setTaskStatus(2);
        task.setTaskStatusName("已完成");

        try {
            processCallbackResult(task, detail);
        } catch (Exception e) {
            log.error("处理回访结果异常，taskId: {}", task.getCallbackTaskId(), e);
        }

        callbackTaskMapper.updateById(task);
        log.info("回访通话已结束，taskId: {}, duration: {}秒", task.getCallbackTaskId(), task.getCallDuration());
    }

    private void handleCallFailed(CallbackTask task, Map<String, Object> detail) {
        task.setCallResult(detail != null ? String.valueOf(detail.get("status")) : "200004");
        task.setCallResultMsg(detail != null ? String.valueOf(detail.get("statusDesc")) : "呼叫失败");
        task.setCallEndTime(LocalDateTime.now());

        int callTimes = task.getCallTimes() != null ? task.getCallTimes() : 0;
        int maxRetry = task.getMaxRetryTimes() != null ? task.getMaxRetryTimes() : 3;

        if (callTimes >= maxRetry) {
            task.setTaskStatus(5);
            task.setTaskStatusName("已过期");
        } else {
            task.setTaskStatus(3);
            task.setTaskStatusName("呼叫失败");
            int retryInterval = callbackConfig.getRetryIntervalMinutes() != null
                    ? callbackConfig.getRetryIntervalMinutes() : 30;
            task.setNextAttemptTime(LocalDateTime.now().plusMinutes(retryInterval));
        }
        callbackTaskMapper.updateById(task);
        log.warn("回访通话失败，taskId: {}, callTimes: {}, status: {}",
                task.getCallbackTaskId(), callTimes, task.getTaskStatusName());
    }

    @Transactional(rollbackFor = Exception.class)
    public CallbackResult processCallbackResult(CallbackTask task, Map<String, Object> detail) {
        CallbackTemplate template = StringUtils.hasText(task.getTemplateId())
                ? selectTemplateByTemplateId(task.getTemplateId())
                : callbackTemplateMapper.selectDefaultTemplate();

        String asrJson = aliyunDyvmsService.getAsrJsonByCallId(task.getCallId());
        String asrFullText = extractCustomerTextFromAsr(asrJson);

        int[] scores = parseThreeIndicatorsFromAsr(asrJson,
                template != null ? template.getKeywordsMap() : null);
        int timelinessScore = scores[0];
        int attitudeScore = scores[1];
        int solvingScore = scores[2];

        double avg = (timelinessScore + attitudeScore + solvingScore) / 3.0;
        int overallScore = (int) Math.round(avg);

        SentimentResultDTO sentiment = null;
        if (StringUtils.hasText(asrFullText)) {
            SentimentAnalysisDTO sentimentDTO = new SentimentAnalysisDTO();
            sentimentDTO.setText(asrFullText);
            sentimentDTO.setExtractKeywords(true);
            sentiment = deepSeekService.analyzeSentiment(sentimentDTO);
        }

        JSONObject aiAnalysis = null;
        List<String> complaintKeywords = new ArrayList<>();
        List<String> praiseKeywords = new ArrayList<>();
        String suggestionText = "";
        String summaryText = "";

        if (StringUtils.hasText(asrFullText)) {
            String systemPrompt = "你是警情回访分析专家，请基于以下通话记录，输出：1) 客户投诉关键词数组 2) 表扬关键词数组 3) 客户建议内容 4) 50字以内回访摘要。严格JSON格式输出{complaintKeywords:[], praiseKeywords:[], suggestionText:'', summaryText:''}";
            String aiResponse = deepSeekService.chat(systemPrompt, asrFullText);
            if (StringUtils.hasText(aiResponse)) {
                try {
                    String jsonStr = extractJsonFromResponse(aiResponse);
                    aiAnalysis = JSON.parseObject(jsonStr);
                    complaintKeywords = aiAnalysis.getList("complaintKeywords", String.class);
                    praiseKeywords = aiAnalysis.getList("praiseKeywords", String.class);
                    suggestionText = aiAnalysis.getString("suggestionText");
                    summaryText = aiAnalysis.getString("summaryText");
                } catch (Exception e) {
                    log.warn("AI回访分析解析失败，taskId: {}", task.getCallbackTaskId(), e);
                }
            }
        }

        int satisfactionLevel;
        if (overallScore >= 5) satisfactionLevel = 1;
        else if (overallScore == 4) satisfactionLevel = 2;
        else if (overallScore == 3) satisfactionLevel = 3;
        else if (overallScore == 2) satisfactionLevel = 4;
        else satisfactionLevel = 5;

        Integer sentimentLabel = sentiment != null ? sentiment.getSentimentLabel() : 1;
        int transferThreshold = callbackConfig.getAutoTransferSentimentThreshold() != null
                ? callbackConfig.getAutoTransferSentimentThreshold() : 0;
        int satisfactionThreshold = callbackConfig.getAutoTransferSatisfactionThreshold() != null
                ? callbackConfig.getAutoTransferSatisfactionThreshold() : 2;
        boolean autoTransferHuman = (overallScore <= satisfactionThreshold)
                || (sentimentLabel != null && sentimentLabel <= transferThreshold)
                || (complaintKeywords != null && !complaintKeywords.isEmpty());

        if (autoTransferHuman) {
            task.setTransferHumanFlag(1);
            task.setTaskStatus(4);
            task.setTaskStatusName("需人工回访");
            task.setTransferHumanTime(LocalDateTime.now());
            task.setTransferHumanReason(buildTransferReason(overallScore, sentimentLabel, complaintKeywords));
            log.info("任务自动转人工，taskId: {}, overallScore: {}, sentimentLabel: {}, complaintCount: {}",
                    task.getCallbackTaskId(), overallScore, sentimentLabel,
                    complaintKeywords != null ? complaintKeywords.size() : 0);
        }

        CallbackResult result = new CallbackResult();
        result.setCallbackResultId(UUID.randomUUID().toString().replace("-", ""));
        result.setCallbackTaskId(task.getCallbackTaskId());
        result.setCallbackTaskNo(task.getCallbackTaskNo());
        result.setCallId(task.getCallId());
        result.setCallDuration(task.getCallDuration());
        result.setAsrJson(asrJson);
        result.setAsrFullText(asrFullText);
        result.setTimelinessScore(timelinessScore);
        result.setAttitudeScore(attitudeScore);
        result.setSolvingScore(solvingScore);
        result.setOverallScore(overallScore);
        result.setSatisfactionLevel(satisfactionLevel);
        result.setSatisfactionLevelName(getSatisfactionLevelName(satisfactionLevel));
        if (sentiment != null) {
            result.setSentimentLabel(sentiment.getSentimentLabel());
            result.setSentimentScore(sentiment.getSentimentScore());
            result.setSentimentKeywords(sentiment.getKeywords() != null ? String.join(",", sentiment.getKeywords()) : null);
            result.setSentimentLabelName(mapSentimentLabelName(sentiment.getSentimentLabel()));
            result.setSentimentAnalysis(sentiment.getSummary());
        }
        result.setComplaintKeywords(complaintKeywords != null && !complaintKeywords.isEmpty() ? String.join(",", complaintKeywords) : null);
        result.setPraiseKeywords(praiseKeywords != null && !praiseKeywords.isEmpty() ? String.join(",", praiseKeywords) : null);
        result.setSuggestionText(suggestionText);
        result.setSummaryText(summaryText);
        result.setAutoTransferHuman(autoTransferHuman ? 1 : 0);
        if (autoTransferHuman) {
            result.setTransferReason(task.getTransferHumanReason());
        }
        if (detail != null && detail.get("recordingUrl") != null) {
            result.setRecordingUrl(String.valueOf(detail.get("recordingUrl")));
        }

        callbackResultMapper.insert(result);
        log.info("保存回访结果成功，taskId: {}, overallScore: {}, satisfaction: {}",
                task.getCallbackTaskId(), overallScore, result.getSatisfactionLevelName());
        return result;
    }

    public int[] parseThreeIndicatorsFromAsr(String asrJson, String keywordsMapStr) {
        int[] result = new int[]{3, 3, 3};
        if (!StringUtils.hasText(asrJson)) {
            return result;
        }

        try {
            Map<Integer, List<String>> scoreKeywords = buildScoreKeywordsMap(keywordsMapStr);
            JSONObject asrObj = JSON.parseObject(asrJson);
            JSONArray segments = asrObj.getJSONArray("segments");
            if (segments == null || segments.isEmpty()) {
                return result;
            }

            List<String> customerTexts = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                JSONObject seg = segments.getJSONObject(i);
                String role = seg.getString("role");
                String text = seg.getString("text");
                if ("客户".equals(role) && StringUtils.hasText(text)) {
                    customerTexts.add(text);
                }
            }

            String allCustomerText = String.join(" ", customerTexts);
            result[0] = calculateScoreByKeywords(allCustomerText, scoreKeywords, result[0]);
            result[1] = calculateScoreByKeywords(allCustomerText, scoreKeywords, result[1]);
            result[2] = calculateScoreByKeywords(allCustomerText, scoreKeywords, result[2]);

            Pattern[] scorePatterns = {
                    Pattern.compile("打?([1-5])分"),
                    Pattern.compile("给?([1-5])分"),
                    Pattern.compile("([1-5])颗?星")
            };
            List<Integer> foundScores = new ArrayList<>();
            for (Pattern p : scorePatterns) {
                java.util.regex.Matcher m = p.matcher(allCustomerText);
                while (m.find()) {
                    foundScores.add(Integer.parseInt(m.group(1)));
                }
            }
            if (foundScores.size() >= 3) {
                Collections.sort(foundScores, Collections.reverseOrder());
                for (int i = 0; i < 3 && i < foundScores.size(); i++) {
                    result[i] = foundScores.get(i);
                }
            } else if (!foundScores.isEmpty()) {
                int avg = (int) Math.round(foundScores.stream().mapToInt(Integer::intValue).average().orElse(3));
                result[0] = result[1] = result[2] = avg;
            }
        } catch (Exception e) {
            log.warn("解析ASR三指标失败，使用默认3分", e);
        }
        return result;
    }

    public int retryFailedTasks(int limit) {
        List<CallbackTask> tasks = callbackTaskMapper.selectNeedRetryTasks(limit);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int success = 0;
        for (CallbackTask task : tasks) {
            try {
                if (dispatchSingleTask(task)) {
                    success++;
                }
            } catch (Exception e) {
                log.error("重试任务异常，taskId: {}", task.getCallbackTaskId(), e);
            }
        }
        if (success > 0) {
            log.info("重试失败任务完成，总数: {}, 成功: {}", tasks.size(), success);
        }
        return success;
    }

    public Map<String, Object> getStatistics(CallbackStatisticsDTO dto) {
        Map<String, Object> result = new LinkedHashMap<>();
        int days = dto.getDays() != null ? dto.getDays() : 30;

        Map<String, Object> today = callbackTaskMapper.countTodayTasks();
        result.put("today", today != null ? today : new LinkedHashMap<>());

        List<Map<String, Object>> satisfactionDist = callbackTaskMapper.countSatisfactionByDays(days);
        result.put("satisfactionDist", satisfactionDist != null ? satisfactionDist : new ArrayList<>());

        Map<String, Object> threeIndicators = callbackTaskMapper.avgThreeIndicators(days);
        result.put("threeIndicators", threeIndicators != null ? threeIndicators : new LinkedHashMap<>());

        List<Map<String, Object>> deptRanking = callbackTaskMapper.countByDept(days, dto.getAreaCode());
        result.put("deptRanking", deptRanking != null ? deptRanking : new ArrayList<>());

        return result;
    }

    private String generateTaskNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%06d", SnowflakeIdUtil.nextId() % 1000000);
        return "CB" + datePart + seq;
    }

    private String mapSourceTypeName(Integer type) {
        if (type == null) return "案件";
        switch (type) {
            case 1: return "结案案件";
            case 2: return "投诉工单";
            case 3: return "重点警情";
            default: return "案件";
        }
    }

    private LocalDateTime adjustToWorkingHours(LocalDateTime time) {
        if (time == null) {
            time = LocalDateTime.now();
        }
        int startHour = callbackConfig.getWorkingHoursStart() != null ? callbackConfig.getWorkingHoursStart() : 9;
        int endHour = callbackConfig.getWorkingHoursEnd() != null ? callbackConfig.getWorkingHoursEnd() : 18;
        LocalTime startTime = LocalTime.of(startHour, 0);
        LocalTime endTime = LocalTime.of(endHour, 0);

        LocalTime t = time.toLocalTime();
        if (t.isBefore(startTime)) {
            return LocalDateTime.of(time.toLocalDate(), startTime);
        } else if (t.isAfter(endTime)) {
            return LocalDateTime.of(time.toLocalDate().plusDays(1), startTime);
        }
        return time;
    }

    private boolean isWithinWorkingHours(LocalDateTime time) {
        int startHour = callbackConfig.getWorkingHoursStart() != null ? callbackConfig.getWorkingHoursStart() : 9;
        int endHour = callbackConfig.getWorkingHoursEnd() != null ? callbackConfig.getWorkingHoursEnd() : 18;
        int hour = time.getHour();
        return hour >= startHour && hour < endHour;
    }

    private String maskName(String name) {
        if (!StringUtils.hasText(name) || name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(Math.max(0, name.length() - 1));
    }

    private LocalDateTime parseDateTime(Object obj) {
        if (obj == null) {
            return LocalDateTime.now();
        }
        try {
            String str = String.valueOf(obj);
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            };
            for (DateTimeFormatter f : formatters) {
                try {
                    return LocalDateTime.parse(str, f);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
    }

    private String extractCustomerTextFromAsr(String asrJson) {
        if (!StringUtils.hasText(asrJson)) {
            return "";
        }
        try {
            JSONObject obj = JSON.parseObject(asrJson);
            JSONArray segments = obj.getJSONArray("segments");
            if (segments == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                JSONObject seg = segments.getJSONObject(i);
                if ("客户".equals(seg.getString("role"))) {
                    String text = seg.getString("text");
                    if (StringUtils.hasText(text)) {
                        sb.append(text).append(" ");
                    }
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private Map<Integer, List<String>> buildScoreKeywordsMap(String keywordsMapStr) {
        Map<Integer, List<String>> map = new LinkedHashMap<>();
        map.put(5, Arrays.asList("非常满意", "很好", "特别好", "满分", "完美", "极好", "点赞", "五星"));
        map.put(4, Arrays.asList("满意", "不错", "挺好", "还可以", "还行", "可以", "挺好的", "四星"));
        map.put(3, Arrays.asList("一般", "普通", "还行吧", "凑合", "马马虎虎", "三星"));
        map.put(2, Arrays.asList("不满意", "不太好", "差", "慢", "不好", "有待改进", "二星"));
        map.put(1, Arrays.asList("非常不满意", "很差", "极差", "糟糕", "投诉", "恶劣", "一星", "举报"));

        if (StringUtils.hasText(keywordsMapStr)) {
            try {
                JSONObject custom = JSON.parseObject(keywordsMapStr);
                for (String key : custom.keySet()) {
                    int score = Integer.parseInt(key);
                    List<String> words = custom.getList(key, String.class);
                    if (words != null && !words.isEmpty()) {
                        map.put(score, words);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private int calculateScoreByKeywords(String text, Map<Integer, List<String>> scoreKeywords, int defaultScore) {
        if (!StringUtils.hasText(text)) {
            return defaultScore;
        }
        int score = defaultScore;
        int maxHits = 0;
        for (Map.Entry<Integer, List<String>> entry : scoreKeywords.entrySet()) {
            int hits = 0;
            for (String kw : entry.getValue()) {
                if (text.contains(kw)) {
                    hits++;
                }
            }
            if (hits > maxHits) {
                maxHits = hits;
                score = entry.getKey();
            }
        }
        return score;
    }

    private String extractJsonFromResponse(String response) {
        if (response == null) {
            return "{}";
        }
        String trimmed = response.trim();
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1);
        }
        return trimmed;
    }

    private String getSatisfactionLevelName(int level) {
        switch (level) {
            case 1: return "非常满意";
            case 2: return "满意";
            case 3: return "一般";
            case 4: return "不满意";
            case 5: return "非常不满意";
            default: return "一般";
        }
    }

    private CallbackTemplate selectTemplateByTemplateId(String templateId) {
        if (!StringUtils.hasText(templateId)) {
            return null;
        }
        LambdaQueryWrapper<CallbackTemplate> tw = new LambdaQueryWrapper<>();
        tw.eq(CallbackTemplate::getTemplateId, templateId);
        return callbackTemplateMapper.selectOne(tw);
    }

    private String mapSentimentLabelName(Integer label) {
        if (label == null) return "中性";
        switch (label) {
            case 0: return "负向";
            case 1: return "中性";
            case 2: return "正向";
            default: return "中性";
        }
    }

    private String buildTransferReason(int overallScore, Integer sentimentLabel, List<String> complaintKeywords) {
        List<String> reasons = new ArrayList<>();
        if (overallScore <= 2) {
            reasons.add("综合评分过低(" + overallScore + "分)");
        } else if (overallScore <= 3) {
            reasons.add("综合评分偏低(" + overallScore + "分)");
        }
        if (sentimentLabel != null && sentimentLabel == 0) {
            reasons.add("情感倾向负向");
        }
        if (complaintKeywords != null && !complaintKeywords.isEmpty()) {
            reasons.add("检测到投诉关键词(" + String.join("/", complaintKeywords) + ")");
        }
        return String.join("；", reasons);
    }
}
