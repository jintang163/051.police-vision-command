package com.police.vision.control.service.intelligence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.config.intelligence.IntelligenceConfig;
import com.police.vision.control.crawler.CrawlerUtils;
import com.police.vision.control.crawler.PublicOpinionPageProcessor;
import com.police.vision.control.crawler.PublicOpinionPipeline;
import com.police.vision.control.dto.SentimentAnalysisDTO;
import com.police.vision.control.dto.SentimentResultDTO;
import com.police.vision.control.entity.intelligence.CrawlerTask;
import com.police.vision.control.entity.intelligence.PublicOpinion;
import com.police.vision.control.mapper.intelligence.CrawlerTaskMapper;
import com.police.vision.control.mapper.intelligence.PublicOpinionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import us.codecraft.webmagic.Spider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final IntelligenceConfig intelligenceConfig;
    private final PublicOpinionMapper publicOpinionMapper;
    private final CrawlerTaskMapper crawlerTaskMapper;
    private final DeepSeekService deepSeekService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeTask(CrawlerTask task) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime startTime = LocalDateTime.now();
        int crawlCount = 0;
        int newCount = 0;

        log.info("开始执行爬虫任务: taskId={}, taskName={}", task.getTaskId(), task.getTaskName());

        PublicOpinionPageProcessor processor = new PublicOpinionPageProcessor(task, intelligenceConfig.getCrawler());
        PublicOpinionPipeline pipeline = new PublicOpinionPipeline();

        String[] entryUrls = parseEntryUrls(task.getEntryUrls(), task.getSiteUrl());
        if (entryUrls == null || entryUrls.length == 0) {
            log.warn("爬虫任务无有效入口URL: taskId={}", task.getTaskId());
            result.put("crawlCount", 0);
            result.put("newCount", 0);
            result.put("duration", 0L);
            updateTaskResult(task, startTime, LocalDateTime.now(), 0, 0);
            return result;
        }

        int threadCount = task.getThreadCount() != null && task.getThreadCount() > 0
                ? task.getThreadCount()
                : intelligenceConfig.getCrawler().getDefaultThreadCount();

        int crawlDepth = task.getCrawlDepth() != null && task.getCrawlDepth() > 0
                ? task.getCrawlDepth()
                : intelligenceConfig.getCrawler().getDefaultCrawlDepth();

        Spider spider = Spider.create(processor)
                .addPipeline(pipeline)
                .thread(threadCount);

        for (String url : entryUrls) {
            spider.addUrl(url);
        }

        try {
            spider.run();
            crawlCount = pipeline.size();
            log.info("爬虫任务采集完成，共爬取{}条结果，开始去重入库", crawlCount);

            List<Map<String, Object>> results = pipeline.getAllResults();
            newCount = processAndSaveResults(results, task);

        } catch (Exception e) {
            log.error("爬虫任务执行异常: taskId={}", task.getTaskId(), e);
        } finally {
            spider.stop();
        }

        LocalDateTime endTime = LocalDateTime.now();
        long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

        updateTaskResult(task, startTime, endTime, crawlCount, newCount);

        result.put("crawlCount", crawlCount);
        result.put("newCount", newCount);
        result.put("duration", durationSeconds);

        log.info("爬虫任务执行完成: taskId={}, crawlCount={}, newCount={}, duration={}s",
                task.getTaskId(), crawlCount, newCount, durationSeconds);

        return result;
    }

    public Map<String, Object> crawlSingleUrl(String url, CrawlerTask defaults) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!StringUtils.hasText(url)) {
            result.put("success", false);
            result.put("message", "URL不能为空");
            return result;
        }

        CrawlerTask task = defaults != null ? defaults : new CrawlerTask();
        task.setEntryUrls(url);
        if (!StringUtils.hasText(task.getTitleSelector())) {
            task.setTitleSelector("title, h1");
        }
        if (!StringUtils.hasText(task.getContentSelector())) {
            task.setContentSelector("article, .content, body");
        }
        if (task.getThreadCount() == null) {
            task.setThreadCount(1);
        }
        if (task.getCrawlDepth() == null) {
            task.setCrawlDepth(1);
        }

        PublicOpinionPageProcessor processor = new PublicOpinionPageProcessor(task, intelligenceConfig.getCrawler());
        PublicOpinionPipeline pipeline = new PublicOpinionPipeline();

        Spider spider = Spider.create(processor)
                .addPipeline(pipeline)
                .thread(1)
                .addUrl(url);

        try {
            spider.run();
        } catch (Exception e) {
            log.error("单页爬取异常: url={}", url, e);
            result.put("success", false);
            result.put("message", "爬取异常: " + e.getMessage());
            return result;
        } finally {
            spider.stop();
        }

        List<Map<String, Object>> results = pipeline.getAllResults();
        result.put("success", true);
        result.put("total", results.size());
        result.put("results", results);

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchSentimentAnalysis(int batchSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        int processed = 0;
        int success = 0;
        int failed = 0;

        int limit = batchSize > 0 ? batchSize : 100;

        LambdaQueryWrapper<PublicOpinion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNull(PublicOpinion::getSentimentLabel)
                .or()
                .eq(PublicOpinion::getSentimentLabel, 0)
                .orderByDesc(PublicOpinion::getCreateTime)
                .last("LIMIT " + limit);

        List<PublicOpinion> opinions = publicOpinionMapper.selectList(queryWrapper);

        if (opinions == null || opinions.isEmpty()) {
            result.put("processed", 0);
            result.put("success", 0);
            result.put("failed", 0);
            result.put("message", "没有待分析的舆情数据");
            return result;
        }

        log.info("开始批量情感分析，待处理数量: {}", opinions.size());

        for (PublicOpinion opinion : opinions) {
            processed++;
            try {
                boolean analyzed = analyzeSingleOpinion(opinion);
                if (analyzed) {
                    publicOpinionMapper.updateById(opinion);
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.error("情感分析失败, opinionId={}", opinion.getOpinionId(), e);
            }

            if (processed % 20 == 0) {
                log.info("批量情感分析进度: {}/{}, success={}, failed={}",
                        processed, opinions.size(), success, failed);
            }
        }

        result.put("processed", processed);
        result.put("success", success);
        result.put("failed", failed);
        log.info("批量情感分析完成, processed={}, success={}, failed={}", processed, success, failed);
        return result;
    }

    private int processAndSaveResults(List<Map<String, Object>> results, CrawlerTask task) {
        int newCount = 0;
        Set<String> processedUrlMd5Set = new HashSet<>();

        for (Map<String, Object> item : results) {
            String url = (String) item.get("url");
            if (!StringUtils.hasText(url)) {
                continue;
            }

            String urlMd5 = CrawlerUtils.md5(url);

            if (processedUrlMd5Set.contains(urlMd5)) {
                continue;
            }
            processedUrlMd5Set.add(urlMd5);

            LambdaQueryWrapper<PublicOpinion> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(PublicOpinion::getSourceUrl, url);
            Long existCount = publicOpinionMapper.selectCount(existWrapper);
            if (existCount != null && existCount > 0) {
                continue;
            }

            PublicOpinion opinion = buildPublicOpinion(item, task, urlMd5);

            if (intelligenceConfig.getCrawler().isAutoSentiment()) {
                try {
                    analyzeSingleOpinion(opinion);
                } catch (Exception e) {
                    log.warn("自动情感分析失败，跳过情感字段, url={}", url, e);
                }
            }

            int inserted = publicOpinionMapper.insert(opinion);
            if (inserted > 0) {
                newCount++;
            }
        }

        return newCount;
    }

    private PublicOpinion buildPublicOpinion(Map<String, Object> item, CrawlerTask task, String urlMd5) {
        PublicOpinion opinion = new PublicOpinion();

        opinion.setOpinionId("OP" + urlMd5);
        opinion.setOpinionNo(SnowflakeIdUtil.generateOrderNo("OP"));

        opinion.setSourceSite(task.getSiteUrl());
        opinion.setSourceSiteName(task.getSiteName());
        opinion.setSourceUrl((String) item.get("url"));

        String title = (String) item.get("title");
        if (title != null && title.length() > 500) {
            title = title.substring(0, 500);
        }
        opinion.setTitle(title);

        String content = (String) item.get("content");
        if (content != null && content.length() > 10000) {
            content = content.substring(0, 10000);
        }
        opinion.setContent(content);

        String author = (String) item.get("author");
        if (author != null && author.length() > 100) {
            author = author.substring(0, 100);
        }
        opinion.setAuthor(author);

        String publishTimeString = (String) item.get("publishTimeString");
        if (StringUtils.hasText(publishTimeString)) {
            LocalDateTime publishTime = CrawlerUtils.parsePublishTime(publishTimeString);
            opinion.setPublishTime(publishTime);
        }

        opinion.setKeywords(task.getKeywords());
        opinion.setCrawlerTaskId(task.getTaskId());

        opinion.setPoliceStationCode(task.getPoliceStationCode());
        opinion.setPoliceStationName(task.getPoliceStationName());

        opinion.setStatus(0);
        opinion.setStatusName("待处理");

        return opinion;
    }

    private boolean analyzeSingleOpinion(PublicOpinion opinion) {
        String text = buildAnalysisText(opinion);
        if (!StringUtils.hasText(text) || text.length() < 10) {
            return false;
        }

        SentimentAnalysisDTO dto = new SentimentAnalysisDTO();
        dto.setText(text);
        dto.setDomain("舆情分析");
        dto.setLanguage("zh");

        SentimentResultDTO sentimentResult = deepSeekService.analyzeSentiment(dto);
        if (sentimentResult == null) {
            return false;
        }

        applySentimentResult(opinion, sentimentResult);
        return true;
    }

    private String buildAnalysisText(PublicOpinion opinion) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(opinion.getTitle())) {
            sb.append("标题：").append(opinion.getTitle()).append("\n");
        }
        if (StringUtils.hasText(opinion.getContent())) {
            String content = opinion.getContent();
            if (content.length() > 3000) {
                content = content.substring(0, 3000);
            }
            sb.append("内容：").append(content);
        }
        return sb.toString();
    }

    private void applySentimentResult(PublicOpinion opinion, SentimentResultDTO result) {
        String label = result.getSentimentLabel();
        if (label != null) {
            switch (label.toLowerCase()) {
                case "positive":
                    opinion.setSentimentLabel(1);
                    opinion.setSentimentLabelName("正面");
                    break;
                case "negative":
                    opinion.setSentimentLabel(-1);
                    opinion.setSentimentLabelName("负面");
                    break;
                case "neutral":
                default:
                    opinion.setSentimentLabel(0);
                    opinion.setSentimentLabelName("中性");
                    break;
            }
        }

        Double score = result.getSentimentScore();
        if (score != null) {
            opinion.setSentimentScore(BigDecimal.valueOf(score));
        }

        List<String> keywords = result.getKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            opinion.setSentimentKeywords(String.join(",", keywords));
            opinion.setKeywords(String.join(",", keywords));
        }

        List<String> topics = result.getTopics();
        if (topics != null && !topics.isEmpty()) {
            opinion.setTopics(String.join(",", topics));
        }

        opinion.setSentimentAnalysis(result.getSummary());
    }

    private void updateTaskResult(CrawlerTask task, LocalDateTime startTime, LocalDateTime endTime,
                                   int crawlCount, int newCount) {
        long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

        task.setLastStartTime(startTime);
        task.setLastEndTime(endTime);
        task.setLastDurationSeconds(durationSeconds);
        task.setLastCrawlCount(crawlCount);
        task.setLastNewCount(newCount);

        if (task.getTotalCrawlCount() == null) {
            task.setTotalCrawlCount(newCount);
        } else {
            task.setTotalCrawlCount(task.getTotalCrawlCount() + newCount);
        }

        task.setTaskStatus(0);
        task.setTaskStatusName("已完成");

        crawlerTaskMapper.updateById(task);
    }

    private String[] parseEntryUrls(String entryUrls, String siteUrl) {
        List<String> urls = new ArrayList<>();

        if (StringUtils.hasText(entryUrls)) {
            for (String url : entryUrls.split("[,，;；\\n\\r\\s]+")) {
                String trimmed = url.trim();
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    urls.add(trimmed);
                }
            }
        }

        if (urls.isEmpty() && StringUtils.hasText(siteUrl)) {
            String trimmed = siteUrl.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                urls.add(trimmed);
            }
        }

        return urls.toArray(new String[0]);
    }
}
