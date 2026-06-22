package com.police.vision.control.crawler;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class PublicOpinionPipeline implements Pipeline {

    @Getter
    private final List<Map<String, Object>> results = new CopyOnWriteArrayList<>();

    @Override
    public void process(ResultItems resultItems, Task task) {
        Map<String, Object> fields = resultItems.getAll();
        if (fields == null || fields.isEmpty()) {
            return;
        }

        String title = resultItems.get("title");
        String url = resultItems.get("url");

        if (title == null || url == null) {
            return;
        }

        Map<String, Object> record = new HashMap<>();
        record.put("title", title);
        record.put("content", resultItems.get("content"));
        record.put("url", url);
        record.put("author", resultItems.get("author"));
        record.put("publishTimeString", resultItems.get("publishTimeString"));
        record.put("html", resultItems.get("html"));

        results.add(record);
        log.debug("Collected result: [{}] - {}", title.length() > 60 ? title.substring(0, 60) + "..." : title, url);
    }

    public int size() {
        return results.size();
    }

    public List<Map<String, Object>> getAllResults() {
        return new ArrayList<>(results);
    }

    public void clear() {
        results.clear();
    }
}
