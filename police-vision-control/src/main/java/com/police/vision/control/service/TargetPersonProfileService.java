package com.police.vision.control.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.neo4j.node.TargetPersonNode;
import com.police.vision.control.neo4j.relation.PersonRelationship;
import com.police.vision.control.neo4j.repository.TargetPersonRepository;
import com.police.vision.control.neo4j.repository.CriminalRecordRepository;
import com.police.vision.control.entity.*;
import com.police.vision.control.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TargetPersonProfileService {

    private final TargetPersonMapper targetPersonMapper;
    private final TargetPersonRepository targetPersonRepository;
    private final CriminalRecordRepository criminalRecordRepository;
    private final FenceAlertMapper fenceAlertMapper;
    private final PersonRelationMapper personRelationMapper;
    private final RedisUtil redisUtil;

    private static final Map<String, Long> FACE_COOLDOWN = new ConcurrentHashMap<>();
    private static final int FACE_ALERT_COOLDOWN_SECONDS = 60;

    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdatePerson(TargetPerson person) {
        TargetPerson existing = null;
        if (person.getIdCardNo() != null) {
            existing = targetPersonMapper.selectByIdCardNo(person.getIdCardNo());
        }
        if (existing != null) {
            person.setId(existing.getId());
            if (person.getPersonId() == null) {
                person.setPersonId(existing.getPersonId());
            }
            targetPersonMapper.updateById(person);
        } else {
            if (person.getPersonId() == null) {
                person.setPersonId("P" + SnowflakeIdUtil.nextId());
            }
            if (person.getId() == null) {
                person.setId(SnowflakeIdUtil.nextId());
            }
            targetPersonMapper.insert(person);
        }
        syncToNeo4j(person);
        redisUtil.delete(RedisConstant.TARGET_PERSON_KEY + "*");
        log.info("添加/更新重点人员：personId={}, personName={}, type={}",
                person.getPersonId(), person.getPersonName(), person.getPersonType());
    }

    private volatile boolean neo4jAvailable = true;
    private volatile long lastNeo4jCheckTime = 0;
    private static final long NEO4J_CHECK_INTERVAL_MS = 30_000;

    public void syncToNeo4j(TargetPerson person) {
        if (!isNeo4jAvailable()) {
            log.warn("Neo4j不可用，写入补偿队列：personId={}", person.getPersonId());
            enqueueNeo4jSync(person.getPersonId());
            return;
        }
        try {
            Optional<TargetPersonNode> opt = targetPersonRepository.findByPersonId(person.getPersonId());
            TargetPersonNode node = opt.orElse(new TargetPersonNode());
            node.setPersonId(person.getPersonId());
            node.setPersonName(person.getPersonName());
            node.setIdCardNo(person.getIdCardNo());
            node.setPersonType(person.getPersonType());
            node.setControlLevel(person.getControlLevel());
            node.setAddress(person.getResidentAddress());
            node.setPhone(person.getPhone());
            node.setGender(person.getGender());
            node.setAge(person.getAge());
            node.setAvatarUrl(person.getAvatarUrl());
            node.setRemark(person.getRemark());
            node.setRiskScore(person.getRiskScore());
            targetPersonRepository.save(node);
            neo4jAvailable = true;
            log.debug("同步重点人员到Neo4j：personId={}", person.getPersonId());
        } catch (Exception e) {
            neo4jAvailable = false;
            lastNeo4jCheckTime = System.currentTimeMillis();
            log.warn("同步重点人员到Neo4j失败，写入补偿队列：personId={}, error={}", person.getPersonId(), e.getMessage());
            enqueueNeo4jSync(person.getPersonId());
        }
    }

    private boolean isNeo4jAvailable() {
        if (neo4jAvailable) return true;
        long now = System.currentTimeMillis();
        if (now - lastNeo4jCheckTime < NEO4J_CHECK_INTERVAL_MS) return false;
        try {
            targetPersonRepository.count();
            neo4jAvailable = true;
            lastNeo4jCheckTime = now;
            log.info("Neo4j恢复可用");
            return true;
        } catch (Exception e) {
            lastNeo4jCheckTime = now;
            return false;
        }
    }

    private void enqueueNeo4jSync(String personId) {
        try {
            String key = "neo4j:sync:queue";
            redisUtil.setObject(key + ":" + personId, personId, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("写入Neo4j补偿队列失败：personId={}", personId, e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addCoCaseRelation(String personId1, String personId2,
                                   String caseId, String caseName, String description) {
        TargetPerson p1 = targetPersonMapper.selectByPersonId(personId1);
        TargetPerson p2 = targetPersonMapper.selectByPersonId(personId2);
        if (p1 == null || p2 == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "关联人员不存在");
        }

        saveRelationToMysql(personId1, personId2, "CO_CASE", "同案人员",
                caseId, caseName, description, 0, 80);

        if (isNeo4jAvailable()) {
            try {
                syncRelationToNeo4j(personId1, personId2, "CO_CASE", "同案人员",
                        caseId, caseName, description, 80);
                log.info("添加同案关系(Neo4j+MySQL)：{} <-> {}，案件：{}", personId1, personId2, caseName);
            } catch (Exception e) {
                log.warn("同案关系写入Neo4j失败(已存MySQL可补偿)：{}", e.getMessage());
            }
        } else {
            log.info("添加同案关系(MySQL降级)：{} <-> {}，案件：{}", personId1, personId2, caseName);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addFrequentContact(String personId1, String personId2,
                                    int contactCount, int strength) {
        TargetPerson p1 = targetPersonMapper.selectByPersonId(personId1);
        TargetPerson p2 = targetPersonMapper.selectByPersonId(personId2);
        if (p1 == null || p2 == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "关联人员不存在");
        }

        saveRelationToMysql(personId1, personId2, "FREQUENT_CONTACT", "频繁联系人",
                null, null, null, contactCount, strength);

        if (isNeo4jAvailable()) {
            try {
                syncRelationToNeo4j(personId1, personId2, "FREQUENT_CONTACT", "频繁联系人",
                        null, null, null, strength);
                log.info("添加频繁联系人关系(Neo4j+MySQL)：{} <-> {}，联系次数：{}", personId1, personId2, contactCount);
            } catch (Exception e) {
                log.warn("频繁联系人关系写入Neo4j失败(已存MySQL可补偿)：{}", e.getMessage());
            }
        } else {
            log.info("添加频繁联系人关系(MySQL降级)：{} <-> {}", personId1, personId2);
        }
    }

    private void saveRelationToMysql(String personId1, String personId2, String relationType,
                                      String relationName, String caseId, String caseName,
                                      String description, int contactCount, int strength) {
        PersonRelation rel = new PersonRelation();
        rel.setId(SnowflakeIdUtil.nextId());
        rel.setRelationId("REL" + SnowflakeIdUtil.nextId());
        rel.setPersonId1(personId1);
        rel.setPersonId2(personId2);
        rel.setRelationType(relationType);
        rel.setRelationName(relationName);
        rel.setCaseId(caseId);
        rel.setCaseName(caseName);
        rel.setDescription(description);
        rel.setContactCount(contactCount);
        rel.setStrength(strength);
        rel.setFirstContactDate(LocalDate.now());
        rel.setLastContactDate(LocalDate.now());
        rel.setSyncedToNeo4j(false);
        personRelationMapper.insert(rel);
    }

    private void syncRelationToNeo4j(String personId1, String personId2, String relationType,
                                       String relationName, String caseId, String caseName,
                                       String description, int strength) {
        try {
            Optional<TargetPersonNode> p1Opt = targetPersonRepository.findByPersonId(personId1);
            Optional<TargetPersonNode> p2Opt = targetPersonRepository.findByPersonId(personId2);
            if (p1Opt.isEmpty() || p2Opt.isEmpty()) return;

            TargetPersonNode p1 = p1Opt.get();
            TargetPersonNode p2 = p2Opt.get();

            PersonRelationship rel = new PersonRelationship();
            rel.setTargetPerson(p2);
            rel.setRelationType(relationType);
            rel.setRelationName(relationName);
            rel.setCaseId(caseId);
            rel.setCaseName(caseName);
            rel.setDescription(description);
            rel.setStrength(strength);
            rel.setFirstContactDate(LocalDate.now());

            PersonRelationship reverse = new PersonRelationship();
            reverse.setTargetPerson(p1);
            reverse.setRelationType(relationType);
            reverse.setRelationName(relationName);
            reverse.setCaseId(caseId);
            reverse.setCaseName(caseName);
            reverse.setDescription(description);
            reverse.setStrength(strength);
            reverse.setFirstContactDate(LocalDate.now());

            if ("CO_CASE".equals(relationType)) {
                p1.getCoCaseRelations().add(rel);
                p2.getCoCaseRelations().add(reverse);
            } else if ("FREQUENT_CONTACT".equals(relationType)) {
                rel.setLastContactDate(LocalDate.now());
                reverse.setLastContactDate(LocalDate.now());
                p1.getFrequentContactRelations().add(rel);
                p2.getFrequentContactRelations().add(reverse);
            }

            targetPersonRepository.save(p1);
            targetPersonRepository.save(p2);
        } catch (Exception e) {
            throw new RuntimeException("Neo4j同步关系失败: " + e.getMessage(), e);
        }
    }

    public TargetPerson getPersonProfile(String personId) {
        TargetPerson person = targetPersonMapper.selectByPersonId(personId);
        if (person == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "人员不存在");
        }
        person.setActivityPattern(calculateActivityPattern(personId));
        person.setAbnormalStats(calculateAbnormalStats(personId));
        return person;
    }

    public Map<String, Object> getRelationGraph(String personId, int depth, int limit) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        TargetPerson person = targetPersonMapper.selectByPersonId(personId);
        if (person == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "人员不存在");
        }

        Map<String, Object> centerNode = new HashMap<>();
        centerNode.put("id", person.getPersonId());
        centerNode.put("label", person.getPersonName());
        centerNode.put("type", person.getPersonType());
        centerNode.put("level", person.getControlLevel());
        centerNode.put("center", true);
        nodes.add(centerNode);

        if (isNeo4jAvailable()) {
            try {
                List<TargetPersonNode> related = targetPersonRepository.findRelatedPersons(personId, limit);
                if (related != null) {
                    for (TargetPersonNode relatedNode : related) {
                        Map<String, Object> node = new HashMap<>();
                        node.put("id", relatedNode.getPersonId());
                        node.put("label", relatedNode.getPersonName());
                        node.put("type", relatedNode.getPersonType());
                        node.put("level", relatedNode.getControlLevel());
                        node.put("riskScore", relatedNode.getRiskScore());
                        nodes.add(node);
                    }

                    List<TargetPersonNode> coCases = targetPersonRepository.findCoCasePersons(personId, limit / 2);
                    if (coCases != null) {
                        for (TargetPersonNode p : coCases) {
                            Map<String, Object> edge = new HashMap<>();
                            edge.put("source", personId);
                            edge.put("target", p.getPersonId());
                            edge.put("relation", "同案人员");
                            edge.put("color", "#ff4d4f");
                            edges.add(edge);
                        }
                    }

                    List<TargetPersonNode> contacts = targetPersonRepository.findFrequentContacts(personId, limit / 2);
                    if (contacts != null) {
                        for (TargetPersonNode p : contacts) {
                            Map<String, Object> edge = new HashMap<>();
                            edge.put("source", personId);
                            edge.put("target", p.getPersonId());
                            edge.put("relation", "频繁联系");
                            edge.put("color", "#faad14");
                            edges.add(edge);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("从Neo4j获取关系图谱失败，降级到MySQL关系表：{}", e.getMessage());
                neo4jAvailable = false;
                lastNeo4jCheckTime = System.currentTimeMillis();
                buildMysqlRelationGraph(personId, limit, nodes, edges);
            }
        } else {
            log.info("Neo4j不可用，直接使用MySQL关系表构建图谱：personId={}", personId);
            buildMysqlRelationGraph(personId, limit, nodes, edges);
        }

        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("center", personId);
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", edges.size());
        return result;
    }

    public Map<String, Object> getActivityStats(String personId, int days) {
        Map<String, Object> stats = new HashMap<>();
        if (days <= 0) days = 30;

        LocalDateTime start = LocalDateTime.now().minusDays(days);
        LocalDateTime end = LocalDateTime.now();

        LambdaQueryWrapper<FenceAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FenceAlert::getPersonId, personId)
                .between(FenceAlert::getAlertTime, start, end);
        List<FenceAlert> alerts = fenceAlertMapper.selectList(wrapper);

        Map<String, Integer> byHour = new LinkedHashMap<>();
        Map<String, Integer> byDayOfWeek = new LinkedHashMap<>();
        Map<String, Integer> byFenceType = new HashMap<>();
        String[] hours = {"00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11",
                "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23"};
        for (String h : hours) byHour.put(h, 0);
        String[] weekdays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (String w : weekdays) byDayOfWeek.put(w, 0);

        for (FenceAlert alert : alerts) {
            LocalDateTime t = alert.getAlertTime();
            String hourKey = String.format("%02d", t.getHour());
            byHour.merge(hourKey, 1, Integer::sum);
            int dow = t.getDayOfWeek().getValue() - 1;
            byDayOfWeek.merge(weekdays[dow], 1, Integer::sum);
            String typeName = alert.getFenceTypeName() != null ? alert.getFenceTypeName() : "其他";
            byFenceType.merge(typeName, 1, Integer::sum);
        }

        stats.put("totalAlerts", alerts.size());
        stats.put("days", days);
        stats.put("byHour", byHour);
        stats.put("byDayOfWeek", byDayOfWeek);
        stats.put("byFenceType", byFenceType);

        List<String> topHours = byHour.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        stats.put("activeHours", topHours);

        return stats;
    }

    private Map<String, Object> calculateActivityPattern(String personId) {
        Map<String, Object> pattern = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthAgo = now.minusDays(30);

        LambdaQueryWrapper<FenceAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FenceAlert::getPersonId, personId)
                .between(FenceAlert::getAlertTime, monthAgo, now);
        List<FenceAlert> alerts = fenceAlertMapper.selectList(wrapper);

        int total = alerts.size();
        int alertCount = 0;
        int abnormal = 0;
        Set<String> fences = new HashSet<>();
        Map<Integer, Integer> hourCounts = new HashMap<>();

        for (FenceAlert a : alerts) {
            if (a.getAlertLevel() != null && a.getAlertLevel() >= 2) alertCount++;
            if (a.getAlertType() != null && a.getAlertType() == 2) abnormal++;
            if (a.getFenceId() != null) fences.add(a.getFenceId());
            if (a.getAlertTime() != null) {
                hourCounts.merge(a.getAlertTime().getHour(), 1, Integer::sum);
            }
        }

        int mostHour = -1;
        int max = 0;
        for (Map.Entry<Integer, Integer> e : hourCounts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                mostHour = e.getKey();
            }
        }

        pattern.put("totalAppearanceCount", total);
        pattern.put("alertCount", alertCount);
        pattern.put("abnormalCount", abnormal);
        pattern.put("visitedFenceCount", fences.size());
        pattern.put("mostActiveHour", mostHour >= 0 ? mostHour + ":00" : "暂无数据");
        pattern.put("avgWeeklyAppearance", total / 4.0);
        return pattern;
    }

    private Map<String, Object> calculateAbnormalStats(String personId) {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime halfYearAgo = now.minusDays(180);

        LambdaQueryWrapper<FenceAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FenceAlert::getPersonId, personId)
                .between(FenceAlert::getAlertTime, halfYearAgo, now);
        List<FenceAlert> alerts = fenceAlertMapper.selectList(wrapper);

        int nightActivity = 0;
        int fenceViolations = 0;
        int highRiskAreas = 0;
        Map<String, Integer> typeCount = new HashMap<>();

        for (FenceAlert a : alerts) {
            int hour = a.getAlertTime() != null ? a.getAlertTime().getHour() : 12;
            if (hour >= 23 || hour < 5) nightActivity++;
            if ("forbidden".equals(a.getFenceType())) fenceViolations++;
            if (a.getAlertLevel() != null && a.getAlertLevel() >= 3) highRiskAreas++;
            String tn = a.getFenceTypeName() != null ? a.getFenceTypeName() : "其他";
            typeCount.merge(tn, 1, Integer::sum);
        }

        stats.put("halfYearTotal", alerts.size());
        stats.put("nightActivityCount", nightActivity);
        stats.put("fenceViolationCount", fenceViolations);
        stats.put("highRiskAreaVisitCount", highRiskAreas);
        stats.put("areaTypeDistribution", typeCount);
        stats.put("riskTrend", alerts.size() > 20 ? "rising" : alerts.size() > 10 ? "stable" : "falling");
        return stats;
    }

    private void buildMysqlRelationGraph(String personId, int limit,
                                          List<Map<String, Object>> nodes,
                                          List<Map<String, Object>> edges) {
        List<PersonRelation> relations = personRelationMapper.selectByPersonId(personId);
        Set<String> addedNodeIds = new HashSet<>();
        addedNodeIds.add(personId);

        for (PersonRelation rel : relations) {
            String otherPersonId = personId.equals(rel.getPersonId1()) ? rel.getPersonId2() : rel.getPersonId1();
            if (addedNodeIds.contains(otherPersonId)) continue;
            if (addedNodeIds.size() > limit) break;

            TargetPerson other = targetPersonMapper.selectByPersonId(otherPersonId);
            if (other == null) continue;

            Map<String, Object> node = new HashMap<>();
            node.put("id", other.getPersonId());
            node.put("label", other.getPersonName());
            node.put("type", other.getPersonType());
            node.put("level", other.getControlLevel());
            node.put("riskScore", other.getRiskScore());
            node.put("source", "mysql_relation");
            nodes.add(node);
            addedNodeIds.add(other.getPersonId());

            Map<String, Object> edge = new HashMap<>();
            edge.put("source", rel.getPersonId1());
            edge.put("target", rel.getPersonId2());
            edge.put("relation", rel.getRelationName());
            edge.put("color", "同案人员".equals(rel.getRelationName()) ? "#ff4d4f" : "#faad14");
            edge.put("strength", rel.getStrength());
            edge.put("degraded", true);
            edges.add(edge);
        }

        if (edges.isEmpty()) {
            TargetPerson center = targetPersonMapper.selectByPersonId(personId);
            if (center != null) {
                List<TargetPerson> fallback = findFallbackRelated(center, limit);
                for (TargetPerson r : fallback) {
                    if (addedNodeIds.contains(r.getPersonId())) continue;
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", r.getPersonId());
                    node.put("label", r.getPersonName());
                    node.put("type", r.getPersonType());
                    node.put("level", r.getControlLevel());
                    node.put("source", "mysql_fallback");
                    nodes.add(node);
                    addedNodeIds.add(r.getPersonId());

                    String relation = inferRelation(center, r);
                    Map<String, Object> edge = new HashMap<>();
                    edge.put("source", personId);
                    edge.put("target", r.getPersonId());
                    edge.put("relation", relation);
                    edge.put("color", "同案人员".equals(relation) ? "#ff4d4f" : "#faad14");
                    edge.put("degraded", true);
                    edge.put("inferred", true);
                    edges.add(edge);
                }
            }
        }
    }

    private List<TargetPerson> findFallbackRelated(TargetPerson person, int limit) {
        LambdaQueryWrapper<TargetPerson> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(TargetPerson::getPersonId, person.getPersonId())
                .eq(TargetPerson::getStatus, 1);
        if (person.getPoliceStationCode() != null) {
            wrapper.eq(TargetPerson::getPoliceStationCode, person.getPoliceStationCode());
        }
        wrapper.last("LIMIT " + limit);
        return targetPersonMapper.selectList(wrapper);
    }

    private String inferRelation(TargetPerson center, TargetPerson related) {
        if (center.getPoliceStationCode() != null
                && center.getPoliceStationCode().equals(related.getPoliceStationCode())
                && "CRIMINAL".equals(center.getPersonType())
                && "CRIMINAL".equals(related.getPersonType())) {
            return "同案人员";
        }
        return "频繁联系";
    }

    public boolean canSendFaceAlert(String personId) {
        String key = "face:cool:" + personId;
        long now = System.currentTimeMillis();
        Long last = FACE_COOLDOWN.get(key);
        if (last != null && (now - last) < FACE_ALERT_COOLDOWN_SECONDS * 1000L) {
            return false;
        }
        FACE_COOLDOWN.put(key, now);
        if (FACE_COOLDOWN.size() > 10000) {
            FACE_COOLDOWN.clear();
        }
        return true;
    }
}
