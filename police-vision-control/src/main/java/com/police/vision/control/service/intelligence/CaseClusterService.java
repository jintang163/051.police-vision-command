package com.police.vision.control.service.intelligence;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.config.intelligence.ElasticsearchConfig;
import com.police.vision.control.config.intelligence.IntelligenceConfig;
import com.police.vision.control.dto.intelligence.ClusterAnalyzeDTO;
import com.police.vision.control.entity.intelligence.CaseCluster;
import com.police.vision.control.entity.intelligence.PoliceCaseInfo;
import com.police.vision.control.mapper.intelligence.CaseClusterMapper;
import com.police.vision.control.mapper.intelligence.PoliceCaseInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseClusterService {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchConfig elasticsearchConfig;
    private final CaseClusterMapper caseClusterMapper;
    private final IntelligenceConfig intelligenceConfig;
    private final PoliceCaseInfoMapper policeCaseInfoMapper;

    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final int MD5_PREFIX_LENGTH = 12;
    private static final int TOP_KEYWORDS_COUNT = 10;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyzeClusters(ClusterAnalyzeDTO dto) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime startTime = LocalDateTime.now();
        List<CaseCluster> clusters = new ArrayList<>();
        int totalCases = 0;

        try {
            IntelligenceConfig.ClusterConfig clusterCfg = intelligenceConfig.getCluster();
            int timeWindowHours = dto.getTimeWindowHours() != null ? dto.getTimeWindowHours() : clusterCfg.getTimeWindowHours();
            double threshold = dto.getSimilarityThreshold() != null ? dto.getSimilarityThreshold().doubleValue() : clusterCfg.getSimilarityThreshold();
            int minClusterSize = dto.getMinClusterSize() != null ? dto.getMinClusterSize() : clusterCfg.getMinClusterSize();

            List<Map<String, Object>> cases;

            if (!isEsEnabled()) {
                log.info("Elasticsearch未启用，尝试从DB查询案件数据进行串并案分析");
                List<PoliceCaseInfo> dbCases = policeCaseInfoMapper.selectByTimeRange(
                        dto.getStartTime() != null ? dto.getStartTime() : LocalDateTime.now().minusHours(timeWindowHours),
                        dto.getEndTime() != null ? dto.getEndTime() : LocalDateTime.now(),
                        dto.getCaseType(),
                        dto.getAreaCode(),
                        10000
                );
                if (dbCases != null && !dbCases.isEmpty()) {
                    cases = dbCases.stream().map(this::convertCaseToMap).collect(Collectors.toList());
                    totalCases = cases.size();
                    log.info("从DB查询到案件数量: {}", totalCases);
                } else {
                    cases = new ArrayList<>();
                }
            } else {
                cases = queryCasesFromEs(dto);
                totalCases = cases.size();
                log.info("从ES查询到案件数量: {}", totalCases);
            }

            if (totalCases < minClusterSize) {
                log.info("案件数量不足，无法形成聚类 (需要至少{}个案件)", minClusterSize);
                result.put("clusters", clusters);
                result.put("totalCases", totalCases);
                result.put("durationMs", Duration.between(startTime, LocalDateTime.now()).toMillis());
                return result;
            }

            List<List<Map<String, Object>>> rawClusters = performClustering(cases, timeWindowHours, threshold);

            for (List<Map<String, Object>> rawCluster : rawClusters) {
                if (rawCluster.size() < minClusterSize) {
                    continue;
                }
                CaseCluster clusterEntity = buildClusterEntity(rawCluster, threshold);
                if (clusterEntity != null) {
                    clusters.add(clusterEntity);
                }
            }

            clusters.sort((a, b) -> Integer.compare(b.getCaseCount(), a.getCaseCount()));

            int maxClusters = clusterCfg.getMaxClusters();
            if (clusters.size() > maxClusters) {
                clusters = clusters.subList(0, maxClusters);
            }

            for (CaseCluster cluster : clusters) {
                try {
                    caseClusterMapper.insert(cluster);
                } catch (Exception e) {
                    log.error("插入聚类结果失败, clusterId={}", cluster.getClusterId(), e);
                }
            }

            log.info("串并案分析完成，生成{}个聚类簇，共{}个案件", clusters.size(), totalCases);

        } catch (Exception e) {
            log.error("串并案分析异常", e);
        }

        result.put("clusters", clusters);
        result.put("totalCases", totalCases);
        result.put("durationMs", Duration.between(startTime, LocalDateTime.now()).toMillis());
        return result;
    }

    public List<Map<String, Object>> searchSimilarCases(String caseId, double threshold, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            if (!isEsEnabled()) {
                log.warn("Elasticsearch未启用，无法查询相似案件");
                return result;
            }

            Map<String, Object> sourceCase = getCaseByIdFromEs(caseId);
            if (sourceCase == null) {
                log.warn("未找到源案件: caseId={}", caseId);
                return result;
            }

            List<Map<String, Object>> allCases = queryAllCasesFromEs();

            List<Map<String, Object>> candidates = allCases.stream()
                    .filter(c -> !caseId.equals(String.valueOf(c.get("case_id"))))
                    .collect(Collectors.toList());

            List<Map.Entry<Map<String, Object>, Double>> scoredList = new ArrayList<>();
            for (Map<String, Object> candidate : candidates) {
                double score = calculateOverallSimilarity(sourceCase, candidate);
                if (score >= threshold) {
                    scoredList.add(new AbstractMap.SimpleEntry<>(candidate, score));
                }
            }

            scoredList.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            int count = 0;
            for (Map.Entry<Map<String, Object>, Double> entry : scoredList) {
                if (limit > 0 && count >= limit) {
                    break;
                }
                Map<String, Object> item = new LinkedHashMap<>(entry.getKey());
                item.put("similarityScore", roundToFour(entry.getValue()));
                result.add(item);
                count++;
            }

            log.info("相似案件查询完成, caseId={}, 找到{}个相似案件", caseId, result.size());

        } catch (Exception e) {
            log.error("查询相似案件异常, caseId={}", caseId, e);
        }

        return result;
    }

    public Map<String, Object> getClusterDetail(String clusterId) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            LambdaQueryWrapper<CaseCluster> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(CaseCluster::getClusterId, clusterId);
            CaseCluster cluster = caseClusterMapper.selectOne(queryWrapper);

            if (cluster == null) {
                log.warn("未找到聚类簇: clusterId={}", clusterId);
                result.put("cluster", null);
                result.put("caseList", new ArrayList<>());
                result.put("timeline", new ArrayList<>());
                result.put("suspects", new ArrayList<>());
                return result;
            }

            result.put("cluster", cluster);

            List<Map<String, Object>> caseList = new ArrayList<>();
            List<Map<String, Object>> timeline = new ArrayList<>();
            Set<String> suspectSet = new LinkedHashSet<>();
            Set<String> vehicleSet = new LinkedHashSet<>();

            if (StringUtils.hasText(cluster.getCaseIds())) {
                String[] caseIdArray = cluster.getCaseIds().split(",");
                if (isEsEnabled()) {
                    for (String cid : caseIdArray) {
                        try {
                            Map<String, Object> caseDetail = getCaseByIdFromEs(cid.trim());
                            if (caseDetail != null) {
                                caseList.add(caseDetail);

                                Map<String, Object> timelineItem = new LinkedHashMap<>();
                                timelineItem.put("caseId", caseDetail.get("case_id"));
                                timelineItem.put("caseNo", caseDetail.get("case_no"));
                                timelineItem.put("caseTime", caseDetail.get("case_time"));
                                timelineItem.put("caseType", caseDetail.get("case_type"));
                                timelineItem.put("address", caseDetail.get("address"));
                                timeline.add(timelineItem);

                                Object suspects = caseDetail.get("suspect_ids");
                                if (suspects != null) {
                                    String[] arr = String.valueOf(suspects).split("[,，;；]");
                                    for (String s : arr) {
                                        if (StringUtils.hasText(s.trim())) {
                                            suspectSet.add(s.trim());
                                        }
                                    }
                                }

                                Object vehicles = caseDetail.get("vehicle_ids");
                                if (vehicles != null) {
                                    String[] arr = String.valueOf(vehicles).split("[,，;；]");
                                    for (String v : arr) {
                                        if (StringUtils.hasText(v.trim())) {
                                            vehicleSet.add(v.trim());
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("查询案件详情失败, caseId={}", cid, e);
                        }
                    }
                } else {
                    for (String cid : caseIdArray) {
                        try {
                            LambdaQueryWrapper<PoliceCaseInfo> caseWrapper = new LambdaQueryWrapper<>();
                            caseWrapper.eq(PoliceCaseInfo::getCaseId, cid.trim());
                            PoliceCaseInfo caseInfo = policeCaseInfoMapper.selectOne(caseWrapper);
                            if (caseInfo != null) {
                                Map<String, Object> caseDetail = convertCaseToMap(caseInfo);
                                caseList.add(caseDetail);

                                Map<String, Object> timelineItem = new LinkedHashMap<>();
                                timelineItem.put("caseId", caseDetail.get("case_id"));
                                timelineItem.put("caseNo", caseDetail.get("case_no"));
                                timelineItem.put("caseTime", caseDetail.get("case_time"));
                                timelineItem.put("caseType", caseDetail.get("case_type"));
                                timelineItem.put("address", caseDetail.get("address"));
                                timeline.add(timelineItem);

                                Object suspects = caseDetail.get("suspect_ids");
                                if (suspects != null) {
                                    String[] arr = String.valueOf(suspects).split("[,，;；]");
                                    for (String s : arr) {
                                        if (StringUtils.hasText(s.trim())) {
                                            suspectSet.add(s.trim());
                                        }
                                    }
                                }

                                Object vehicles = caseDetail.get("vehicle_ids");
                                if (vehicles != null) {
                                    String[] arr = String.valueOf(vehicles).split("[,，;；]");
                                    for (String v : arr) {
                                        if (StringUtils.hasText(v.trim())) {
                                            vehicleSet.add(v.trim());
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("从DB查询案件详情失败, caseId={}", cid, e);
                        }
                    }
                }
            }

            timeline.sort((a, b) -> {
                String ta = String.valueOf(a.get("caseTime"));
                String tb = String.valueOf(b.get("caseTime"));
                return ta.compareTo(tb);
            });

            Map<String, Object> suspectsInfo = new LinkedHashMap<>();
            suspectsInfo.put("suspectIds", new ArrayList<>(suspectSet));
            suspectsInfo.put("suspectCount", suspectSet.size());
            suspectsInfo.put("vehicleIds", new ArrayList<>(vehicleSet));
            suspectsInfo.put("vehicleCount", vehicleSet.size());

            result.put("caseList", caseList);
            result.put("timeline", timeline);
            result.put("suspects", suspectsInfo);

        } catch (Exception e) {
            log.error("获取聚类详情异常, clusterId={}", clusterId, e);
        }

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markClusterHandled(String clusterId, String statusName, String remark, Long officerId) {
        try {
            LambdaQueryWrapper<CaseCluster> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(CaseCluster::getClusterId, clusterId);
            CaseCluster cluster = caseClusterMapper.selectOne(queryWrapper);

            if (cluster == null) {
                log.warn("未找到聚类簇: clusterId={}", clusterId);
                return false;
            }

            cluster.setStatus(2);
            cluster.setStatusName(StringUtils.hasText(statusName) ? statusName : "已处理");
            cluster.setHandleRemark(remark);
            cluster.setHandleOfficerId(officerId);
            cluster.setHandleTime(LocalDateTime.now());

            int updated = caseClusterMapper.updateById(cluster);
            log.info("标记聚类簇已处理: clusterId={}, result={}", clusterId, updated > 0);
            return updated > 0;

        } catch (Exception e) {
            log.error("标记聚类簇处理状态异常, clusterId={}", clusterId, e);
            return false;
        }
    }

    private boolean isEsEnabled() {
        return elasticsearchClient != null && elasticsearchConfig.isEnabled();
    }

    private List<Map<String, Object>> queryCasesFromEs(ClusterAnalyzeDTO dto) {
        List<Map<String, Object>> cases = new ArrayList<>();

        if (!isEsEnabled()) {
            return cases;
        }

        try {
            String caseIndex = elasticsearchConfig.getCaseIndex();

            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

            LocalDateTime startTime = dto.getStartTime();
            LocalDateTime endTime = dto.getEndTime();
            if (startTime == null) {
                int hours = dto.getTimeWindowHours() != null ? dto.getTimeWindowHours() : intelligenceConfig.getCluster().getTimeWindowHours();
                startTime = LocalDateTime.now().minusHours(hours);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            Map<String, Object> rangeMap = new LinkedHashMap<>();
            rangeMap.put("gte", startTime.toString());
            rangeMap.put("lte", endTime.toString());
            rangeMap.put("format", "strict_date_optional_time||epoch_millis");

            Query rangeQuery = Query.of(q -> q
                    .range(r -> r
                            .field("case_time")
                            .gte(startTime.toString())
                            .lte(endTime.toString())
                    )
            );
            boolBuilder.must(rangeQuery);

            if (StringUtils.hasText(dto.getCaseType())) {
                Query termQuery = Query.of(q -> q
                        .term(t -> t
                                .field("case_type")
                                .value(dto.getCaseType())
                        )
                );
                boolBuilder.must(termQuery);
            }

            if (StringUtils.hasText(dto.getAreaCode())) {
                Query wildcardQuery = Query.of(q -> q
                        .wildcard(w -> w
                                .field("area_code")
                                .value(dto.getAreaCode() + "*")
                        )
                );
                boolBuilder.must(wildcardQuery);
            }

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(caseIndex)
                            .query(q -> q.bool(boolBuilder.build()))
                            .size(10000)
                            .scroll(sc -> sc.time("1m"))
                    , Map.class
            );

            extractHits(response, cases);

            String scrollId = response.scrollId();
            while (response.hits().hits().size() > 0 && scrollId != null) {
                final String currentScrollId = scrollId;
                response = elasticsearchClient.scroll(sc -> sc
                                .scrollId(currentScrollId)
                                .time(t -> t.time("1m"))
                        , Map.class
                );
                extractHits(response, cases);
                scrollId = response.scrollId();
                if (response.hits().hits().size() == 0) {
                    break;
                }
            }

            if (scrollId != null) {
                final String clearScrollId = scrollId;
                try {
                    elasticsearchClient.clearScroll(c -> c.scrollId(clearScrollId));
                } catch (Exception e) {
                    log.warn("清除scroll上下文失败", e);
                }
            }

        } catch (Exception e) {
            log.warn("从ES查询案件失败", e);
        }

        return cases;
    }

    private List<Map<String, Object>> queryAllCasesFromEs() {
        List<Map<String, Object>> cases = new ArrayList<>();

        if (!isEsEnabled()) {
            return cases;
        }

        try {
            String caseIndex = elasticsearchConfig.getCaseIndex();

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(caseIndex)
                            .query(q -> q.matchAll(m -> m))
                            .size(10000)
                    , Map.class
            );

            extractHits(response, cases);

        } catch (Exception e) {
            log.warn("从ES查询全部案件失败", e);
        }

        return cases;
    }

    private Map<String, Object> getCaseByIdFromEs(String caseId) {
        if (!isEsEnabled() || !StringUtils.hasText(caseId)) {
            return null;
        }

        try {
            String caseIndex = elasticsearchConfig.getCaseIndex();

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index(caseIndex)
                            .query(q -> q.term(t -> t.field("case_id").value(caseId)))
                            .size(1)
                    , Map.class
            );

            List<Hit<Map>> hits = response.hits().hits();
            if (!hits.isEmpty()) {
                Hit<Map> hit = hits.get(0);
                return convertHitToMap(hit);
            }

        } catch (Exception e) {
            log.warn("从ES查询案件详情失败, caseId={}", caseId, e);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private void extractHits(SearchResponse<Map> response, List<Map<String, Object>> collector) {
        try {
            List<Hit<Map>> hits = response.hits().hits();
            for (Hit<Map> hit : hits) {
                Map<String, Object> caseMap = convertHitToMap(hit);
                if (caseMap != null) {
                    collector.add(caseMap);
                }
            }
        } catch (Exception e) {
            log.warn("解析ES搜索结果失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertHitToMap(Hit<Map> hit) {
        try {
            Map<String, Object> source = hit.source();
            if (source == null) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>(source);
            if (hit.id() != null && !result.containsKey("case_id")) {
                result.put("case_id", hit.id());
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private List<List<Map<String, Object>>> performClustering(List<Map<String, Object>> cases,
                                                              int timeWindowHours,
                                                              double threshold) {
        int n = cases.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }

        long timeWindowMillis = (long) timeWindowHours * 60 * 60 * 1000;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Map<String, Object> ci = cases.get(i);
                Map<String, Object> cj = cases.get(j);

                if (!isWithinTimeWindow(ci, cj, timeWindowMillis)) {
                    continue;
                }

                double sim = calculateOverallSimilarity(ci, cj);
                if (sim >= threshold) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, List<Map<String, Object>>> clusterMap = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            clusterMap.computeIfAbsent(root, k -> new ArrayList<>()).add(cases.get(i));
        }

        return new ArrayList<>(clusterMap.values());
    }

    private int find(int[] parent, int x) {
        if (parent[x] != x) {
            parent[x] = find(parent, parent[x]);
        }
        return parent[x];
    }

    private void union(int[] parent, int x, int y) {
        int xr = find(parent, x);
        int yr = find(parent, y);
        if (xr != yr) {
            parent[yr] = xr;
        }
    }

    private boolean isWithinTimeWindow(Map<String, Object> c1, Map<String, Object> c2, long windowMillis) {
        try {
            LocalDateTime t1 = parseCaseTime(c1.get("case_time"));
            LocalDateTime t2 = parseCaseTime(c2.get("case_time"));
            if (t1 == null || t2 == null) {
                return true;
            }
            long diff = Math.abs(Duration.between(t1, t2).toMillis());
            return diff <= windowMillis;
        } catch (Exception e) {
            return true;
        }
    }

    private LocalDateTime parseCaseTime(Object timeObj) {
        if (timeObj == null) {
            return null;
        }
        try {
            if (timeObj instanceof LocalDateTime) {
                return (LocalDateTime) timeObj;
            }
            String timeStr = String.valueOf(timeObj);
            if (timeStr.length() >= 19) {
                return LocalDateTime.parse(timeStr.substring(0, 19).replace('T', ' '),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            return LocalDateTime.parse(timeStr);
        } catch (Exception e) {
            try {
                long epochMillis = Long.parseLong(String.valueOf(timeObj));
                return LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(epochMillis),
                        ZoneId.systemDefault()
                );
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private double calculateOverallSimilarity(Map<String, Object> c1, Map<String, Object> c2) {
        double modusScore = calculateModusSimilarity(c1, c2);
        double geoScore = calculateGeoSimilarity(c1, c2);
        double typeScore = calculateTypeSimilarity(c1, c2);
        double weaponScore = calculateWeaponSimilarity(c1, c2);
        double targetScore = calculateTargetSimilarity(c1, c2);

        double weighted = (modusScore * 0.40)
                + (geoScore * 0.25)
                + (typeScore * 0.15)
                + (weaponScore * 0.10)
                + (targetScore * 0.10);

        return Math.min(1.0, Math.max(0.0, weighted));
    }

    private double calculateModusSimilarity(Map<String, Object> c1, Map<String, Object> c2) {
        String mo1 = getStringValue(c1, "modus_operandi");
        String mo2 = getStringValue(c2, "modus_operandi");

        if (!StringUtils.hasText(mo1) && !StringUtils.hasText(mo2)) {
            return 0.5;
        }
        if (!StringUtils.hasText(mo1) || !StringUtils.hasText(mo2)) {
            return 0.0;
        }

        Set<String> tokens1 = tokenizeModusOperandi(mo1);
        Set<String> tokens2 = tokenizeModusOperandi(mo2);

        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 0.5;
        }

        double jaccard = calculateJaccard(tokens1, tokens2);
        double editSim = calculateEditDistanceSimilarity(mo1, mo2);

        return (jaccard * 0.6) + (editSim * 0.4);
    }

    private Set<String> tokenizeModusOperandi(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return tokens;
        }

        String cleaned = text.replaceAll("[\\s\\p{Punct}]+", "");
        for (int i = 0; i < cleaned.length() - 1; i++) {
            tokens.add(cleaned.substring(i, i + 2));
        }

        String[] parts = text.split("[\\s\\p{Punct}，。、；：]+");
        for (String p : parts) {
            if (p.length() >= 2) {
                tokens.add(p);
            }
        }

        return tokens;
    }

    private double calculateJaccard(Set<String> s1, Set<String> s2) {
        if (s1.isEmpty() && s2.isEmpty()) {
            return 0.5;
        }
        Set<String> intersection = new HashSet<>(s1);
        intersection.retainAll(s2);
        Set<String> union = new HashSet<>(s1);
        union.addAll(s2);
        if (union.isEmpty()) {
            return 0.0;
        }
        return (double) intersection.size() / union.size();
    }

    private double calculateEditDistanceSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    private double calculateGeoSimilarity(Map<String, Object> c1, Map<String, Object> c2) {
        try {
            Double lon1 = getDoubleValue(c1, "longitude");
            Double lat1 = getDoubleValue(c1, "latitude");
            Double lon2 = getDoubleValue(c2, "longitude");
            Double lat2 = getDoubleValue(c2, "latitude");

            if (lon1 == null || lat1 == null || lon2 == null || lat2 == null) {
                String ac1 = getStringValue(c1, "area_code");
                String ac2 = getStringValue(c2, "area_code");
                if (StringUtils.hasText(ac1) && StringUtils.hasText(ac2) && ac1.equals(ac2)) {
                    return 0.6;
                }
                return 0.3;
            }

            double distance = haversineDistance(lat1, lon1, lat2, lon2);

            double maxRadius = 5000.0;
            if (distance <= 500) {
                return 1.0;
            } else if (distance <= maxRadius) {
                return 1.0 - (distance / maxRadius);
            } else {
                return 0.0;
            }

        } catch (Exception e) {
            return 0.3;
        }
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    private double calculateTypeSimilarity(Map<String, Object> c1, Map<String, Object> c2) {
        String t1 = getStringValue(c1, "case_type");
        String t2 = getStringValue(c2, "case_type");
        if (!StringUtils.hasText(t1) || !StringUtils.hasText(t2)) {
            return 0.5;
        }
        return t1.equals(t2) ? 1.0 : 0.0;
    }

    private double calculateWeaponSimilarity(Map<String, Object> c1, Map<String, Object> c2) {
        String w1 = getStringValue(c1, "weapon_type");
        String w2 = getStringValue(c2, "weapon_type");
        if (!StringUtils.hasText(w1) && !StringUtils.hasText(w2)) {
            return 0.5;
        }
        if (!StringUtils.hasText(w1) || !StringUtils.hasText(w2)) {
            return 0.0;
        }
        return w1.equals(w2) ? 1.0 : 0.0;
    }

    private double calculateTargetSimilarity(Map<String, Object> c1, Map<String, Object> c2) {
        String t1 = getStringValue(c1, "target_type");
        String t2 = getStringValue(c2, "target_type");
        if (!StringUtils.hasText(t1) && !StringUtils.hasText(t2)) {
            return 0.5;
        }
        if (!StringUtils.hasText(t1) || !StringUtils.hasText(t2)) {
            return 0.0;
        }
        return t1.equals(t2) ? 1.0 : 0.0;
    }

    private CaseCluster buildClusterEntity(List<Map<String, Object>> clusterCases, double threshold) {
        if (clusterCases == null || clusterCases.isEmpty()) {
            return null;
        }

        try {
            CaseCluster cluster = new CaseCluster();

            String signature = buildClusterSignature(clusterCases);
            String clusterId = "CC" + md5FirstN(signature, MD5_PREFIX_LENGTH);
            cluster.setClusterId(clusterId);

            cluster.setClusterNo(SnowflakeIdUtil.nextIdStr());

            String caseType = getDominantCaseType(clusterCases);
            cluster.setCaseType(caseType);
            cluster.setCaseTypeName(getCaseTypeName(caseType));

            String modusOperandi = mergeModusOperandi(clusterCases);
            cluster.setModusOperandi(modusOperandi);
            cluster.setModusKeywords(extractTopKeywords(clusterCases, TOP_KEYWORDS_COUNT));

            LocalDateTime[] timeRange = getClusterTimeRange(clusterCases);
            cluster.setStartTime(timeRange[0]);
            cluster.setEndTime(timeRange[1]);

            int caseCount = clusterCases.size();
            cluster.setCaseCount(caseCount);

            cluster.setCaseIds(joinFieldValues(clusterCases, "case_id"));
            cluster.setCaseNos(joinFieldValues(clusterCases, "case_no"));

            String areaCode = getDominantAreaCode(clusterCases);
            cluster.setAreaCode(areaCode);

            double[] center = calculateClusterCenter(clusterCases);
            if (center != null) {
                cluster.setCenterLongitude(roundToFourBigDecimal(center[0]));
                cluster.setCenterLatitude(roundToFourBigDecimal(center[1]));
                double radius = calculateClusterRadius(clusterCases, center[0], center[1]) * 1.2;
                cluster.setRadiusMeters(roundToFourBigDecimal(radius));
            }

            cluster.setSuspectIds(mergeAndDeduplicate(clusterCases, "suspect_ids"));
            cluster.setVehicleIds(mergeAndDeduplicate(clusterCases, "vehicle_ids"));

            double avgSim = calculateAverageSimilarity(clusterCases);
            cluster.setSimilarityScore(roundToFourBigDecimal(avgSim));

            int alertLevel = calculateAlertLevel(caseCount, caseType);
            cluster.setAlertLevel(alertLevel);
            cluster.setAlertLevelName(getAlertLevelName(alertLevel));

            cluster.setInvestigationSuggestion(generateSuggestion(caseType, modusOperandi, caseCount));

            cluster.setStatus(0);
            cluster.setStatusName("待处理");

            cluster.setAnalysisModelId("DBSCAN_HYBRID_001");
            cluster.setAnalysisModelName("DBSCAN混合相似度聚类");

            cluster.setClusterName(generateClusterName(caseType, areaCode, caseCount));

            return cluster;

        } catch (Exception e) {
            log.error("构建聚类实体失败", e);
            return null;
        }
    }

    private String buildClusterSignature(List<Map<String, Object>> cases) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> c : cases) {
            sb.append(getStringValue(c, "case_id")).append("|");
        }
        return sb.toString();
    }

    private String md5FirstN(String input, int n) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, Math.min(n, hex.length())).toUpperCase();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis()).substring(0, Math.min(n, 13));
        }
    }

    private String getDominantCaseType(List<Map<String, Object>> cases) {
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (Map<String, Object> c : cases) {
            String type = getStringValue(c, "case_type");
            if (StringUtils.hasText(type)) {
                countMap.merge(type, 1, Integer::sum);
            }
        }
        return countMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private String getCaseTypeName(String caseType) {
        if (!StringUtils.hasText(caseType)) {
            return "未知";
        }
        Map<String, String> typeMap = new LinkedHashMap<>();
        typeMap.put("01", "盗窃");
        typeMap.put("02", "抢劫");
        typeMap.put("03", "抢夺");
        typeMap.put("04", "诈骗");
        typeMap.put("05", "故意伤害");
        typeMap.put("06", "故意杀人");
        typeMap.put("07", "强奸");
        typeMap.put("08", "绑架");
        typeMap.put("09", "贩毒");
        typeMap.put("10", "交通肇事");
        typeMap.put("11", "寻衅滋事");
        typeMap.put("12", "聚众斗殴");
        return typeMap.getOrDefault(caseType, "其他");
    }

    private String mergeModusOperandi(List<Map<String, Object>> cases) {
        Set<String> sentences = new LinkedHashSet<>();
        for (Map<String, Object> c : cases) {
            String mo = getStringValue(c, "modus_operandi");
            if (StringUtils.hasText(mo)) {
                for (String s : mo.split("[。！？!?；;\\n]+")) {
                    String trimmed = s.trim();
                    if (trimmed.length() >= 2) {
                        sentences.add(trimmed);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String s : sentences) {
            if (count >= 5) {
                break;
            }
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(s);
            count++;
        }
        String result = sb.toString();
        return result.length() > 1000 ? result.substring(0, 1000) : result;
    }

    private String extractTopKeywords(List<Map<String, Object>> cases, int topN) {
        Map<String, Integer> freqMap = new LinkedHashMap<>();
        for (Map<String, Object> c : cases) {
            String mo = getStringValue(c, "modus_operandi");
            if (StringUtils.hasText(mo)) {
                Set<String> tokens = tokenizeModusOperandi(mo);
                for (String t : tokens) {
                    if (t.length() >= 2) {
                        freqMap.merge(t, 1, Integer::sum);
                    }
                }
            }
            String kw = getStringValue(c, "case_keywords");
            if (StringUtils.hasText(kw)) {
                for (String k : kw.split("[,，;；\\s]+")) {
                    String trimmed = k.trim();
                    if (trimmed.length() >= 2) {
                        freqMap.merge(trimmed, 1, Integer::sum);
                    }
                }
            }
        }

        return freqMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
    }

    private LocalDateTime[] getClusterTimeRange(List<Map<String, Object>> cases) {
        LocalDateTime min = null;
        LocalDateTime max = null;
        for (Map<String, Object> c : cases) {
            LocalDateTime t = parseCaseTime(c.get("case_time"));
            if (t != null) {
                if (min == null || t.isBefore(min)) {
                    min = t;
                }
                if (max == null || t.isAfter(max)) {
                    max = t;
                }
            }
        }
        if (min == null) {
            min = LocalDateTime.now();
            max = LocalDateTime.now();
        }
        return new LocalDateTime[]{min, max};
    }

    private String joinFieldValues(List<Map<String, Object>> cases, String fieldName) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, Object> c : cases) {
            Object val = c.get(fieldName);
            if (val != null) {
                String str = String.valueOf(val).trim();
                if (StringUtils.hasText(str)) {
                    values.add(str);
                }
            }
        }
        return String.join(",", values);
    }

    private String getDominantAreaCode(List<Map<String, Object>> cases) {
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (Map<String, Object> c : cases) {
            String code = getStringValue(c, "area_code");
            if (StringUtils.hasText(code)) {
                countMap.merge(code, 1, Integer::sum);
            }
        }
        return countMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private double[] calculateClusterCenter(List<Map<String, Object>> cases) {
        double sumLon = 0.0;
        double sumLat = 0.0;
        int count = 0;
        for (Map<String, Object> c : cases) {
            Double lon = getDoubleValue(c, "longitude");
            Double lat = getDoubleValue(c, "latitude");
            if (lon != null && lat != null) {
                sumLon += lon;
                sumLat += lat;
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return new double[]{sumLon / count, sumLat / count};
    }

    private double calculateClusterRadius(List<Map<String, Object>> cases, double centerLon, double centerLat) {
        double maxDist = 0.0;
        for (Map<String, Object> c : cases) {
            Double lon = getDoubleValue(c, "longitude");
            Double lat = getDoubleValue(c, "latitude");
            if (lon != null && lat != null) {
                double dist = haversineDistance(centerLat, centerLon, lat, lon);
                if (dist > maxDist) {
                    maxDist = dist;
                }
            }
        }
        return maxDist;
    }

    private String mergeAndDeduplicate(List<Map<String, Object>> cases, String fieldName) {
        Set<String> merged = new LinkedHashSet<>();
        for (Map<String, Object> c : cases) {
            Object val = c.get(fieldName);
            if (val != null) {
                String str = String.valueOf(val);
                for (String s : str.split("[,，;；\\s]+")) {
                    String trimmed = s.trim();
                    if (StringUtils.hasText(trimmed)) {
                        merged.add(trimmed);
                    }
                }
            }
        }
        return String.join(",", merged);
    }

    private double calculateAverageSimilarity(List<Map<String, Object>> cases) {
        int n = cases.size();
        if (n < 2) {
            return 1.0;
        }
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                sum += calculateOverallSimilarity(cases.get(i), cases.get(j));
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private int calculateAlertLevel(int caseCount, String caseType) {
        int baseLevel = 1;

        Set<String> seriousTypes = new HashSet<>(Arrays.asList("02", "03", "05", "06", "07", "08", "09"));
        if (StringUtils.hasText(caseType) && seriousTypes.contains(caseType)) {
            baseLevel = 2;
        }

        if (caseCount > 10) {
            return Math.max(baseLevel, 4);
        } else if (caseCount > 5) {
            return Math.max(baseLevel, 3);
        } else if (caseCount > 3) {
            return Math.max(baseLevel, 2);
        }
        return baseLevel;
    }

    private String getAlertLevelName(int level) {
        switch (level) {
            case 1:
                return "L1-一般";
            case 2:
                return "L2-关注";
            case 3:
                return "L3-预警";
            case 4:
                return "L4-严重";
            default:
                return "L1-一般";
        }
    }

    private String generateSuggestion(String caseType, String modusOperandi, int caseCount) {
        List<String> suggestions = new ArrayList<>();

        if (caseCount >= 5) {
            suggestions.add("该系列案件数量较多，建议成立专案组进行并案侦查");
        } else if (caseCount >= 3) {
            suggestions.add("存在系列作案特征，建议重点关注并案可能性");
        }

        suggestions.add("重点排查惯犯、有相似手法前科人员，比对同类案件信息");

        if (StringUtils.hasText(modusOperandi)) {
            if (modusOperandi.contains("撬") || modusOperandi.contains("技术开锁")) {
                suggestions.add("排查有开锁技术、开锁工具的重点人员");
            }
            if (modusOperandi.contains("蒙面") || modusOperandi.contains("伪装")) {
                suggestions.add("调阅周边监控，排查伪装人员特征");
            }
            if (modusOperandi.contains("驾车") || modusOperandi.contains("车辆")) {
                suggestions.add("排查案发时段周边可疑车辆轨迹");
            }
        }

        Set<String> seriousTypes = new HashSet<>(Arrays.asList("02", "03", "06", "07", "08"));
        if (seriousTypes.contains(caseType)) {
            suggestions.add("案件性质恶劣，建议加大侦查力度，尽快锁定嫌疑人");
        }

        suggestions.add("加强案发重点区域巡逻防控，预防同类案件再次发生");

        return String.join("；", suggestions);
    }

    private String generateClusterName(String caseType, String areaCode, int caseCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(getCaseTypeName(caseType)).append("系列案");
        if (StringUtils.hasText(areaCode)) {
            sb.append("-").append(areaCode);
        }
        sb.append("-").append(caseCount).append("起");
        return sb.toString();
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return "";
        }
        Object val = map.get(key);
        return val == null ? "" : String.valueOf(val).trim();
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        if (val == null) {
            return null;
        }
        try {
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
            return Double.parseDouble(String.valueOf(val).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private double roundToFour(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private BigDecimal roundToFourBigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private Map<String, Object> convertCaseToMap(PoliceCaseInfo caseInfo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("case_id", caseInfo.getCaseId());
        map.put("case_no", caseInfo.getCaseNo());
        map.put("case_type", caseInfo.getCaseType());
        map.put("case_type_name", caseInfo.getCaseTypeName());
        map.put("modus_operandi", caseInfo.getModusOperandi());
        map.put("case_keywords", caseInfo.getCaseKeywords());
        map.put("area_code", caseInfo.getAreaCode());
        map.put("longitude", caseInfo.getLongitude() != null ? caseInfo.getLongitude().doubleValue() : null);
        map.put("latitude", caseInfo.getLatitude() != null ? caseInfo.getLatitude().doubleValue() : null);
        map.put("address", caseInfo.getAddress());
        map.put("case_time", caseInfo.getCaseTime());
        map.put("weapon_type", caseInfo.getWeaponType());
        map.put("target_type", caseInfo.getTargetType());
        map.put("suspect_ids", caseInfo.getSuspectIds());
        map.put("vehicle_ids", caseInfo.getVehicleIds());
        map.put("grid_code", caseInfo.getGridCode());
        map.put("is_solved", caseInfo.getIsSolved());
        return map;
    }
}
