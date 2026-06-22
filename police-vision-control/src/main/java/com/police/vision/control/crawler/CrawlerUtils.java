package com.police.vision.control.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CrawlerUtils {

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy年MM月dd日 HH:mm:ss",
            "yyyy年MM月dd日 HH:mm",
            "yyyy年MM月dd日",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "MM-dd HH:mm",
            "MM月dd日 HH:mm"
    };

    private static final List<DateTimeFormatter> FORMATTERS = new ArrayList<>();

    static {
        for (String pattern : DATE_PATTERNS) {
            FORMATTERS.add(DateTimeFormatter.ofPattern(pattern));
        }
    }

    private static final Pattern DATE_PATTERN_REGEX = Pattern.compile(
            "(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}[日]?\\s*\\d{0,2}:?\\d{0,2}:?\\d{0,2})"
    );

    public static String extractPureText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        try {
            Document document = Jsoup.parse(html);
            document.select("script, style, noscript, iframe").remove();
            String cleanText = Jsoup.clean(document.body() != null ? document.body().html() : html,
                    "", Safelist.none(), new Document.OutputSettings().prettyPrint(false));
            cleanText = cleanText.replaceAll("[\\r\\n\\t]+", " ")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            return cleanText;
        } catch (Exception e) {
            return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        }
    }

    public static String md5(String str) {
        if (!StringUtils.hasText(str)) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    public static LocalDateTime parsePublishTime(String str) {
        if (!StringUtils.hasText(str)) {
            return null;
        }
        String cleaned = str.trim();

        String extracted = extractDateString(cleaned);
        if (extracted != null) {
            cleaned = extracted;
        }

        cleaned = normalizeDateString(cleaned);

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(cleaned, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                if (cleaned.length() <= 10 && !cleaned.contains(":")) {
                    cleaned = cleaned + " 00:00:00";
                }
                return LocalDateTime.parse(cleaned, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private static String extractDateString(String text) {
        Matcher matcher = DATE_PATTERN_REGEX.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String normalizeDateString(String dateStr) {
        String result = dateStr;
        result = result.replaceAll("年", "-").replaceAll("月", "-").replaceAll("日", "");
        result = result.replaceAll("/", "-");
        result = result.replaceAll("\\s+", " ");
        result = result.trim();
        return result;
    }
}
