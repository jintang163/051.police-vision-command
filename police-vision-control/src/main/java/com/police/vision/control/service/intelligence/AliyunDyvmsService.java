package com.police.vision.control.service.intelligence;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.police.vision.control.config.intelligence.AliyunDyvmsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunDyvmsService {

    private final AliyunDyvmsConfig aliyunDyvmsConfig;

    public com.aliyun.dyvmsapi20170525.Client createClient() {
        try {
            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                    .setAccessKeyId(aliyunDyvmsConfig.getAccessKeyId())
                    .setAccessKeySecret(aliyunDyvmsConfig.getAccessKeySecret());
            config.setEndpoint(aliyunDyvmsConfig.getEndpoint());
            return new com.aliyun.dyvmsapi20170525.Client(config);
        } catch (Exception e) {
            log.error("创建阿里云语音客户端失败", e);
            return null;
        }
    }

    public String singleCallByTts(String calledNumber, String calledShowNumber, String ttsCode, Map<String, String> ttsParam) {
        if (!aliyunDyvmsConfig.isEnabled()) {
            String mockCallId = "MOCK_" + System.currentTimeMillis();
            log.info("阿里云语音未启用，返回mock callId: {}, calledNumber: {}", mockCallId, calledNumber);
            return mockCallId;
        }

        try {
            com.aliyun.dyvmsapi20170525.Client client = createClient();
            if (client == null) {
                return null;
            }

            com.aliyun.dyvmsapi20170525.models.SingleCallByTtsRequest request = new com.aliyun.dyvmsapi20170525.models.SingleCallByTtsRequest()
                    .setCalledNumber(calledNumber)
                    .setCalledShowNumber(calledShowNumber != null ? calledShowNumber : aliyunDyvmsConfig.getCalledShowNumber())
                    .setTtsCode(ttsCode);

            if (ttsParam != null && !ttsParam.isEmpty()) {
                request.setTtsParam(JSON.toJSONString(ttsParam));
            }

            com.aliyun.dyvmsapi20170525.models.SingleCallByTtsResponse response = client.singleCallByTts(request);
            if (response != null && response.getBody() != null && StringUtils.hasText(response.getBody().getCallId())) {
                String callId = response.getBody().getCallId();
                log.info("阿里云语音外呼成功，callId: {}, calledNumber: {}", callId, calledNumber);
                return callId;
            } else {
                log.warn("阿里云语音外呼响应异常，calledNumber: {}, response: {}", calledNumber, response);
                return null;
            }
        } catch (Exception e) {
            log.error("阿里云语音外呼失败，calledNumber: {}", calledNumber, e);
            return null;
        }
    }

    public Map<String, Object> queryCallDetail(String callId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!aliyunDyvmsConfig.isEnabled()) {
            return mockCallDetail(callId);
        }

        try {
            com.aliyun.dyvmsapi20170525.Client client = createClient();
            if (client == null) {
                return result;
            }

            com.aliyun.dyvmsapi20170525.models.QueryCallDetailByCallIdRequest request = new com.aliyun.dyvmsapi20170525.models.QueryCallDetailByCallIdRequest()
                    .setCallId(callId)
                    .setQueryDate(Long.parseLong(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))));

            com.aliyun.dyvmsapi20170525.models.QueryCallDetailByCallIdResponse response = client.queryCallDetailByCallId(request);
            if (response != null && response.getBody() != null) {
                JSONObject body = JSON.parseObject(JSON.toJSONString(response.getBody()));
                JSONObject callDetail = body.getJSONObject("callDetail");
                if (callDetail != null) {
                    result.put("status", callDetail.getString("statusCode"));
                    result.put("statusDesc", callDetail.getString("statusMsg"));
                    result.put("startTime", callDetail.getString("startTime"));
                    result.put("endTime", callDetail.getString("endTime"));
                    result.put("duration", callDetail.getInteger("duration"));
                    result.put("recordingUrl", callDetail.getString("recordUrl"));
                }
            }
            log.info("查询通话详情成功，callId: {}, status: {}", callId, result.get("status"));
        } catch (Exception e) {
            log.error("查询通话详情失败，callId: {}", callId, e);
        }
        return result;
    }

    public String getAsrJsonByCallId(String callId) {
        if (!aliyunDyvmsConfig.isEnabled()) {
            Random random = new Random();
            int t = random.nextInt(5) + 1;
            int a = random.nextInt(5) + 1;
            int s = random.nextInt(5) + 1;
            return mockAsrJson(t, a, s);
        }

        try {
            com.aliyun.dyvmsapi20170525.Client client = createClient();
            if (client == null) {
                return null;
            }

            com.aliyun.dyvmsapi20170525.models.DownloadRecordDataRequest request = new com.aliyun.dyvmsapi20170525.models.DownloadRecordDataRequest()
                    .setCallId(callId)
                    .setQueryDate(Long.parseLong(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))));

            com.aliyun.dyvmsapi20170525.models.DownloadRecordDataResponse response = client.downloadRecordData(request);
            if (response != null && response.getBody() != null) {
                JSONObject body = JSON.parseObject(JSON.toJSONString(response.getBody()));
                Object asrData = body.get("asrData");
                if (asrData != null) {
                    log.info("获取ASR成功，callId: {}", callId);
                    return JSON.toJSONString(asrData);
                }
                String recordUrl = body.getString("recordUrl");
                if (StringUtils.hasText(recordUrl)) {
                    log.info("ASR不可用，从录音URL模拟，callId: {}", callId);
                    return generateMockAsrFromRecording(recordUrl);
                }
            }
        } catch (Exception e) {
            log.error("获取ASR失败，callId: {}", callId, e);
        }
        return null;
    }

    public boolean stopCallTask(String callId) {
        if (!aliyunDyvmsConfig.isEnabled()) {
            log.info("阿里云语音未启用，mock取消通话，callId: {}", callId);
            return true;
        }

        try {
            com.aliyun.dyvmsapi20170525.Client client = createClient();
            if (client == null) {
                return false;
            }

            com.aliyun.dyvmsapi20170525.models.CancelCallTaskRequest request = new com.aliyun.dyvmsapi20170525.models.CancelCallTaskRequest()
                    .setCallId(callId);

            com.aliyun.dyvmsapi20170525.models.CancelCallTaskResponse response = client.cancelCallTask(request);
            boolean success = response != null && response.getBody() != null
                    && "OK".equalsIgnoreCase(response.getBody().getCode());
            log.info("取消通话结果，callId: {}, success: {}", callId, success);
            return success;
        } catch (Exception e) {
            log.error("取消通话失败，callId: {}", callId, e);
            return false;
        }
    }

    private Map<String, Object> mockCallDetail(String callId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Random random = new Random();
        int rand = random.nextInt(100);

        String status;
        String statusDesc;
        if (rand < 60) {
            status = "200005";
            statusDesc = "已结束";
        } else if (rand < 80) {
            status = "200003";
            statusDesc = "未接听";
        } else {
            status = "200004";
            statusDesc = "呼叫失败";
        }

        result.put("status", status);
        result.put("statusDesc", statusDesc);

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if ("200005".equals(status)) {
            LocalDateTime startTime = now.minusMinutes(2 + random.nextInt(3));
            LocalDateTime endTime = now;
            int duration = (int) java.time.Duration.between(startTime, endTime).getSeconds();
            result.put("startTime", startTime.format(formatter));
            result.put("endTime", endTime.format(formatter));
            result.put("duration", duration);
            result.put("recordingUrl", "http://mock-record.oss-cn-hangzhou.aliyuncs.com/" + callId + ".mp3");
        } else {
            result.put("startTime", now.minusMinutes(1).format(formatter));
            result.put("endTime", now.format(formatter));
            result.put("duration", 0);
            result.put("recordingUrl", "");
        }
        return result;
    }

    private String generateMockAsrFromRecording(String recordUrl) {
        Random random = new Random();
        int t = random.nextInt(3) + 3;
        int a = random.nextInt(3) + 3;
        int s = random.nextInt(3) + 3;
        return mockAsrJson(t, a, s);
    }

    private String mockAsrJson(int t, int a, int s) {
        JSONArray segments = new JSONArray();

        addAsrSegment(segments, "坐席", "您好，这里是公安局警情回访中心，请问是" + randomName() + "女士/先生吗？");
        addAsrSegment(segments, "客户", "嗯，是的，我是。");
        addAsrSegment(segments, "坐席", "您好，耽误您几分钟时间，对您之前报警的警情做个电话回访可以吗？");
        addAsrSegment(segments, "客户", "可以的，你说。");

        addAsrSegment(segments, "坐席", "第一个问题：您觉得我们出警的速度是否及时？请用1-5分评价，5分表示非常满意。");
        addAsrSegment(segments, "客户", scoreToWords(t) + "，我打" + t + "分。");

        addAsrSegment(segments, "坐席", "第二个问题：您对处警民警的服务态度满意吗？");
        addAsrSegment(segments, "客户", "态度" + scoreToWords(a) + "，给" + a + "分。");

        addAsrSegment(segments, "坐席", "第三个问题：您觉得问题处理得怎么样？是否解决了您的诉求？");
        addAsrSegment(segments, "客户", "问题解决得" + scoreToWords(s) + "，打" + s + "分。");

        addAsrSegment(segments, "坐席", "感谢您的评价，请问您还有什么意见或建议吗？");
        if (t >= 4 && a >= 4 && s >= 4) {
            addAsrSegment(segments, "客户", "没有了，民警们很辛苦，感谢你们！");
        } else if (t <= 2 || a <= 2 || s <= 2) {
            addAsrSegment(segments, "客户", "有些建议，希望出警能再快一点，还有希望多跟我们沟通一下进展。");
        } else {
            addAsrSegment(segments, "客户", "暂时没有什么，总体还行吧。");
        }

        addAsrSegment(segments, "坐席", "好的，非常感谢您的配合和宝贵意见，我们会持续改进工作。祝您生活愉快，再见！");
        addAsrSegment(segments, "客户", "好的，再见。");

        JSONObject asr = new JSONObject();
        asr.put("callId", "MOCK_" + System.currentTimeMillis());
        asr.put("segments", segments);
        asr.put("totalDuration", 180);
        asr.put("silenceDuration", 15);
        asr.put("speechRate", 160);
        asr.put("generateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return asr.toJSONString();
    }

    private void addAsrSegment(JSONArray array, String role, String text) {
        JSONObject seg = new JSONObject();
        seg.put("role", role);
        seg.put("text", text);
        seg.put("startTime", System.currentTimeMillis() - 180000 + array.size() * 15000);
        seg.put("endTime", System.currentTimeMillis() - 180000 + array.size() * 15000 + 4000);
        array.add(seg);
    }

    private String randomName() {
        String[] names = {"张", "李", "王", "赵", "陈", "刘", "杨", "黄", "周", "吴"};
        String[] given = {"伟", "芳", "娜", "敏", "静", "强", "磊", "洋", "艳", "军"};
        return names[new Random().nextInt(names.length)] + given[new Random().nextInt(given.length)];
    }

    private String scoreToWords(int score) {
        switch (score) {
            case 5:
                return "非常及时/很好";
            case 4:
                return "挺及时的/还不错";
            case 3:
                return "一般/还可以";
            case 2:
                return "有点慢/不太好";
            case 1:
                return "很慢/很差";
            default:
                return "一般";
        }
    }
}
