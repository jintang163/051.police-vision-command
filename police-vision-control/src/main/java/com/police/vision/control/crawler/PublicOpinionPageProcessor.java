package com.police.vision.control.crawler;

import com.police.vision.control.config.intelligence.IntelligenceConfig;
import com.police.vision.control.entity.intelligence.CrawlerTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Html;
import us.codecraft.webmagic.selector.Selectable;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class PublicOpinionPageProcessor implements PageProcessor {

    private static final String DEFAULT_TITLE_SELECTOR = "title, h1, .title, .article-title, .news-title";
    private static final String DEFAULT_CONTENT_SELECTOR = "article, .content, .article-content, .news-content, #content, .post-content";
    private static final String DEFAULT_AUTHOR_SELECTOR = ".author, .news-author, .article-author, .source, #author";
    private static final String DEFAULT_PUBLISH_TIME_SELECTOR = ".time, .publish-time, .pub-time, .article-time, .news-time, .date, .pub-date";

    private final CrawlerTask crawlerTask;
    private final IntelligenceConfig.CrawlerConfig crawlerConfig;
    private final Site site;
    private final Pattern urlPattern;

    public PublicOpinionPageProcessor(CrawlerTask crawlerTask, IntelligenceConfig.CrawlerConfig crawlerConfig) {
        this.crawlerTask = crawlerTask;
        this.crawlerConfig = crawlerConfig;

        this.site = buildSite(crawlerTask, crawlerConfig);

        String pattern = crawlerTask.getUrlPattern();
        if (StringUtils.hasText(pattern)) {
            this.urlPattern = Pattern.compile(pattern);
        } else {
            this.urlPattern = null;
        }
    }

    private Site buildSite(CrawlerTask task, IntelligenceConfig.CrawlerConfig config) {
        Site s = Site.me()
                .setUserAgent(config.getUserAgent())
                .setTimeOut(config.getDefaultTimeoutSeconds() * 1000)
                .setRetryTimes(3)
                .setRetrySleepTime(1000)
                .setCycleRetryTimes(2)
                .setSleepTime(task.getSleepMillis() != null ? task.getSleepMillis() : config.getDefaultSleepMillis())
                .setCharset("UTF-8")
                .setUseGzip(true);

        String allowDomains = task.getAllowDomains();
        if (StringUtils.hasText(allowDomains)) {
            for (String domain : allowDomains.split("[,，;；\\s]+")) {
                String trimmed = domain.trim();
                if (!trimmed.isEmpty()) {
                    s.addDomain(trimmed);
                }
            }
        }

        return s;
    }

    @Override
    public void process(Page page) {
        String pageUrl = page.getUrl().get();
        log.debug("Processing page: {}", pageUrl);

        extractFollowLinks(page);
        extractContent(page);
    }

    private void extractFollowLinks(Page page) {
        List<String> allLinks = page.getHtml().links().all();
        if (allLinks == null || allLinks.isEmpty()) {
            return;
        }

        for (String link : allLinks) {
            if (!isValidUrl(link)) {
                continue;
            }
            page.addTargetRequest(link);
        }
    }

    private boolean isValidUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        if (url.startsWith("javascript:") || url.startsWith("#") || url.startsWith("mailto:") || url.startsWith("tel:")) {
            return false;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        if (urlPattern != null) {
            return urlPattern.matcher(url).matches();
        }
        return true;
    }

    private void extractContent(Page page) {
        Html html = page.getHtml();

        String titleSelector = StringUtils.hasText(crawlerTask.getTitleSelector())
                ? crawlerTask.getTitleSelector()
                : DEFAULT_TITLE_SELECTOR;
        String contentSelector = StringUtils.hasText(crawlerTask.getContentSelector())
                ? crawlerTask.getContentSelector()
                : DEFAULT_CONTENT_SELECTOR;
        String authorSelector = StringUtils.hasText(crawlerTask.getAuthorSelector())
                ? crawlerTask.getAuthorSelector()
                : DEFAULT_AUTHOR_SELECTOR;
        String publishTimeSelector = StringUtils.hasText(crawlerTask.getPublishTimeSelector())
                ? crawlerTask.getPublishTimeSelector()
                : DEFAULT_PUBLISH_TIME_SELECTOR;

        String title = extractFirstText(html, titleSelector);
        if (!StringUtils.hasText(title)) {
            title = html.xpath("//title/text()").get();
        }
        if (!StringUtils.hasText(title)) {
            page.setSkip(true);
            log.debug("Skip page (no title): {}", page.getUrl().get());
            return;
        }

        String content = extractHtmlOrText(html, contentSelector);
        if (!StringUtils.hasText(content)) {
            content = html.body() != null ? html.body().get() : "";
        }

        String pureText = CrawlerUtils.extractPureText(content);
        if (!StringUtils.hasText(pureText) || pureText.length() < 50) {
            if (!isArticlePage(page.getUrl().get())) {
                page.setSkip(true);
                return;
            }
        }

        String author = extractFirstText(html, authorSelector);
        String publishTimeString = extractFirstText(html, publishTimeSelector);

        page.putField("title", title);
        page.putField("content", pureText);
        page.putField("url", page.getUrl().get());
        page.putField("author", author);
        page.putField("publishTimeString", publishTimeString);
        page.putField("html", html.get());
    }

    private String extractFirstText(Html html, String selectors) {
        if (!StringUtils.hasText(selectors)) {
            return null;
        }
        for (String selector : selectors.split(",")) {
            String trimmed = selector.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                Selectable selectable = html.$(trimmed);
                if (selectable == null) {
                    continue;
                }
                String text = selectable.get();
                if (StringUtils.hasText(text)) {
                    String result = CrawlerUtils.extractPureText(text);
                    if (StringUtils.hasText(result)) {
                        return result;
                    }
                }
            } catch (Exception e) {
                log.warn("CSS selector error [{}] failed: {}", trimmed, e.getMessage());
            }
        }
        return null;
    }

    private String extractHtmlOrText(Html html, String selectors) {
        if (!StringUtils.hasText(selectors)) {
            return null;
        }
        for (String selector : selectors.split(",")) {
            String trimmed = selector.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                Selectable selectable = html.$(trimmed);
                if (selectable == null) {
                    continue;
                }
                String result = selectable.get();
                if (StringUtils.hasText(result)) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("CSS selector error [{}] failed: {}", trimmed, e.getMessage());
            }
        }
        return null;
    }

    private boolean isArticlePage(String url) {
        if (urlPattern != null) {
            return urlPattern.matcher(url).matches();
        }
        String lower = url.toLowerCase();
        return lower.contains("/article/") || lower.contains("/news/") || lower.contains("/detail/")
                || lower.contains("/content/") || lower.contains("/post/") || lower.contains("/show/")
                || lower.contains("article") || lower.contains("news") || lower.contains("detail")
                || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".shtml");
    }

    @Override
    public Site getSite() {
        return site;
    }

    public CrawlerTask getCrawlerTask() {
        return crawlerTask;
    }
}
