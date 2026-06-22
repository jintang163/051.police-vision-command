package com.police.vision.control.service.intelligence;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.police.vision.control.config.intelligence.DeepSeekConfig;
import com.police.vision.control.dto.intelligence.ReportGenerateDTO;
import com.police.vision.control.dto.intelligence.SentimentAnalysisDTO;
import com.police.vision.control.dto.intelligence.SentimentResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekService {

    private final DeepSeekConfig deepSeekConfig;

    private CloseableHttpClient createHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(deepSeekConfig.getConnectTimeout(), TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(deepSeekConfig.getReadTimeout(), TimeUnit.SECONDS))
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    public String chat(String systemPrompt, String userPrompt) {
        if (!deepSeekConfig.isEnabled()) {
            log.warn("DeepSeek服务未启用");
            return null;
        }

        int retryCount = deepSeekConfig.getRetryCount();
        Exception lastException = null;

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                return doChatRequest(systemPrompt, userPrompt);
            } catch (Exception e) {
                lastException = e;
                log.warn("DeepSeek聊天请求失败，第{}/{}次尝试，错误：{}",
                        attempt, retryCount, e.getMessage());
                if (attempt < retryCount) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("DeepSeek聊天请求最终失败，已重试{}次", retryCount, lastException);
        return null;
    }

    private String doChatRequest(String systemPrompt, String userPrompt) throws Exception {
        try (CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost httpPost = new HttpPost(deepSeekConfig.getApiUrl());
            httpPost.setHeader("Authorization", "Bearer " + deepSeekConfig.getApiKey());
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", deepSeekConfig.getModel());
            requestBody.put("max_tokens", deepSeekConfig.getMaxTokens());
            requestBody.put("temperature", deepSeekConfig.getTemperature());
            requestBody.put("top_p", deepSeekConfig.getTopP());

            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            httpPost.setEntity(new StringEntity(
                    requestBody.toJSONString(),
                    ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)
            ));

            return httpClient.execute(httpPost, response -> {
                String responseStr = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JSONObject jsonResponse = JSON.parseObject(responseStr);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject message = choice.getJSONObject("message");
                    if (message != null) {
                        return message.getString("content");
                    }
                }
                log.error("DeepSeek响应格式异常：{}", responseStr);
                return null;
            });
        }
    }

    public String generateReport(ReportGenerateDTO dto, Map<String, Object> multiSourceData) {
        String systemPrompt = buildReportSystemPrompt();
        String userPrompt = buildReportUserPrompt(dto, multiSourceData);
        String result = chat(systemPrompt, userPrompt);
        if (result == null) {
            log.error("情报产品报告生成失败，productType={}", dto.getProductType());
        } else {
            log.info("情报产品报告生成成功，productType={}, areaCode={}",
                    dto.getProductType(), dto.getAreaCode());
        }
        return result;
    }

    private String buildReportSystemPrompt() {
        return """
                你是一名资深公安情报分析师，擅长从多源数据中挖掘有价值的情报信息。
                请根据提供的多源数据，生成一份专业的公安情报产品报告。

                输出要求：
                1. 输出格式为 Markdown
                2. 必须包含以下章节（如无对应数据请标注"数据不足"）：
                   - ## 一、情报摘要（高度概括核心发现和关键结论）
                   - ## 二、警情统计（按类型、区域、时段统计分析）
                   - ## 三、案件分析（重点案件梳理、作案手法分析、串并案分析）
                   - ## 四、重点人员（重点关注人员清单、活动轨迹分析）
                   - ## 五、车辆态势（重点车辆、异常车辆活动分析）
                   - ## 六、舆情分析（网络舆情热度、敏感话题、传播路径）
                   - ## 七、热点区域（治安热点区域分布图、风险等级评估）
                   - ## 八、治安预测（短期治安趋势预判、风险预警）
                   - ## 九、工作建议（针对性的防控措施、警力部署建议）
                3. 内容要客观、准确、基于数据说话，避免主观臆断
                4. 关键数据请用表格或列表呈现
                5. 使用专业的公安术语
                """;
    }

    private String buildReportUserPrompt(ReportGenerateDTO dto, Map<String, Object> multiSourceData) {
        StringBuilder sb = new StringBuilder();
        sb.append("报告类型：").append(dto.getProductType()).append("\n");
        if (dto.getReportStartDate() != null && dto.getReportEndDate() != null) {
            sb.append("统计周期：").append(dto.getReportStartDate()).append(" 至 ").append(dto.getReportEndDate()).append("\n");
        }
        if (dto.getAreaCode() != null) {
            sb.append("区域编码：").append(dto.getAreaCode()).append("\n");
        }
        sb.append("\n以下是多源数据（JSON格式）：\n");
        sb.append("```json\n");
        sb.append(JSON.toJSONString(multiSourceData));
        sb.append("\n```\n\n");
        sb.append("请基于以上数据，按照系统提示的格式要求，生成完整的情报产品报告。");
        return sb.toString();
    }

    public SentimentResultDTO analyzeSentiment(SentimentAnalysisDTO dto) {
        String systemPrompt = buildSentimentSystemPrompt();
        String userPrompt = buildSentimentUserPrompt(dto);
        String response = chat(systemPrompt, userPrompt);

        if (response == null) {
            log.error("情感分析失败，文本长度={}", dto.getText() != null ? dto.getText().length() : 0);
            return null;
        }

        try {
            String jsonStr = extractJsonFromResponse(response);
            JSONObject json = JSON.parseObject(jsonStr);

            SentimentResultDTO result = new SentimentResultDTO();
            String labelStr = json.getString("sentimentLabel");
            int labelValue = 1;
            if ("positive".equalsIgnoreCase(labelStr) || "2".equals(labelStr)) {
                labelValue = 2;
            } else if ("negative".equalsIgnoreCase(labelStr) || "0".equals(labelStr)) {
                labelValue = 0;
            } else {
                labelValue = 1;
            }
            result.setSentimentLabel(labelValue);
            Double scoreVal = json.getDouble("sentimentScore");
            if (scoreVal != null) {
                result.setSentimentScore(BigDecimal.valueOf(scoreVal));
            }
            result.setKeywords(json.getList("keywords", String.class));
            result.setTopics(json.getList("topics", String.class));
            result.setSummary(json.getString("summary"));

            log.info("情感分析完成，sentimentLabel={}, score={}",
                    result.getSentimentLabel(), result.getSentimentScore());
            return result;
        } catch (Exception e) {
            log.error("情感分析结果解析失败，响应内容：{}", response, e);
            return null;
        }
    }

    private String buildSentimentSystemPrompt() {
        return """
                你是一名专业的舆情情感分析专家。请对输入的文本进行情感分析。

                输出要求：
                1. 仅输出 JSON 格式，不要包含其他解释文字或 Markdown 标记
                2. JSON 结构如下：
                {
                  "sentimentLabel": "情感标签，取值为：positive/neutral/negative",
                  "sentimentScore": 情感分值，范围0-1的浮点数，0表示极度负面，1表示极度正面，0.5表示中性,
                  "keywords": ["关键词1", "关键词2", "关键词3"],
                  "topics": ["话题1", "话题2"],
                  "summary": "一句话概括文本主要内容和情感倾向"
                }
                3. sentimentScore 精度保留两位小数
                4. keywords 提取 3-8 个最关键的词汇
                5. topics 提取 1-3 个主要话题类别
                """;
    }

    private String buildSentimentUserPrompt(SentimentAnalysisDTO dto) {
        StringBuilder sb = new StringBuilder();
        if (dto.getDomain() != null && !dto.getDomain().isEmpty()) {
            sb.append("领域：").append(dto.getDomain()).append("\n");
        }
        if (dto.getLanguage() != null && !dto.getLanguage().isEmpty()) {
            sb.append("语言：").append(dto.getLanguage()).append("\n");
        }
        sb.append("\n待分析文本：\n").append(dto.getText()).append("\n\n");
        sb.append("请严格按照系统提示的 JSON 格式输出情感分析结果。");
        return sb.toString();
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

    public Map<String, Object> extractInsights(String text, String category) {
        String systemPrompt = buildInsightSystemPrompt(category);
        String userPrompt = buildInsightUserPrompt(text, category);
        String response = chat(systemPrompt, userPrompt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("originalTextLength", text != null ? text.length() : 0);

        if (response == null) {
            log.error("结构化要点提炼失败，category={}", category);
            result.put("success", false);
            result.put("errorMessage", "调用AI服务失败");
            return result;
        }

        try {
            String jsonStr = extractJsonFromResponse(response);
            JSONObject json = JSON.parseObject(jsonStr);

            result.put("success", true);
            result.put("summary", json.getString("summary"));
            result.put("keyPoints", json.getList("keyPoints", String.class));
            result.put("entities", json.get("entities"));
            result.put("relations", json.get("relations"));
            result.put("riskIndicators", json.get("riskIndicators"));
            result.put("actionItems", json.getList("actionItems", String.class));

            log.info("结构化要点提炼完成，category={}, 要点数={}",
                    category,
                    result.get("keyPoints") != null ? ((List<?>) result.get("keyPoints")).size() : 0);
            return result;
        } catch (Exception e) {
            log.error("结构化要点解析失败，category={}，响应内容：{}", category, response, e);
            result.put("success", false);
            result.put("errorMessage", "解析响应失败：" + e.getMessage());
            result.put("rawResponse", response);
            return result;
        }
    }

    private String buildInsightSystemPrompt(String category) {
        return String.format("""
                你是一名专业的公安情报结构化分析专家。请从给定文本中提炼结构化要点。

                分析类别：%s

                输出要求：
                1. 仅输出 JSON 格式，不要包含其他解释文字或 Markdown 标记
                2. JSON 结构如下：
                {
                  "summary": "一句话高度概括文本核心内容",
                  "keyPoints": ["要点1", "要点2", "要点3"],
                  "entities": {
                    "persons": ["人名1", "人名2"],
                    "locations": ["地点1", "地点2"],
                    "organizations": ["组织1"],
                    "vehicles": ["车牌号1"],
                    "phones": ["手机号1"],
                    "dates": ["日期1"]
                  },
                  "relations": ["实体A与实体B的关系描述"],
                  "riskIndicators": [{"type": "风险类型", "level": "high/medium/low", "description": "风险描述"}],
                  "actionItems": ["建议采取的行动项1", "行动项2"]
                }
                3. keyPoints 提炼 5-10 条最核心的要点
                4. entities 中各字段如无对应实体则为空数组
                5. riskIndicators 识别潜在的治安风险点并标注风险等级
                """, category != null ? category : "通用");
    }

    private String buildInsightUserPrompt(String text, String category) {
        StringBuilder sb = new StringBuilder();
        sb.append("文本类别：").append(category != null ? category : "未指定").append("\n\n");
        sb.append("待分析文本：\n").append(text != null ? text : "").append("\n\n");
        sb.append("请严格按照系统提示的 JSON 格式输出结构化分析结果。");
        return sb.toString();
    }
}
