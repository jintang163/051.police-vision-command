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

    public void syncToNeo4j(TargetPerson person) {
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
            log.debug("同步重点人员到Neo4j：personId={}", person.getPersonId());
        } catch (Exception e) {
            log.warn("同步重点人员到Neo4j失败（Neo4j可能未启动）：{}", e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addCoCaseRelation(String personId1, String personId2,
                                   String caseId, String caseName, String description) {
        try {
            Optional<TargetPersonNode> p1Opt = targetPersonRepository.findByPersonId(personId1);
            Optional<TargetPersonNode> p2Opt = targetPersonRepository.findByPersonId(personId2);
            if (p1Opt.isEmpty() || p2Opt.isEmpty()) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "关联人员不存在");
            }
            TargetPersonNode p1 = p1Opt.get();
            TargetPersonNode p2 = p2Opt.get();

            PersonRelationship rel = new PersonRelationship();
            rel.setTargetPerson(p2);
            rel.setRelationType("CO_CASE");
            rel.setRelationName("同案人员");
            rel.setCaseId(caseId);
            rel.setCaseName(caseName);
            rel.setDescription(description);
            rel.setFirstContactDate(LocalDate.now());
            rel.setStrength(80);
            p1.getCoCaseRelations().add(rel);

            PersonRelationship reverse = new PersonRelationship();
            reverse.setTargetPerson(p1);
            reverse.setRelationType("CO_CASE");
            reverse.setRelationName("同案人员");
            reverse.setCaseId(caseId);
            reverse.setCaseName(caseName);
            reverse.setDescription(description);
            reverse.setFirstContactDate(LocalDate.now());
            reverse.setStrength(80);
            p2.getCoCaseRelations().add(reverse);

            targetPersonRepository.save(p1);
            targetPersonRepository.save(p2);
            log.info("添加同案关系：{} <-> {}，案件：{}", personId1, personId2, caseName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("添加同案关系失败（Neo4j可能未启动）：{}", e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addFrequentContact(String personId1, String personId2,
                                    int contactCount, int strength) {
        try {
            Optional<TargetPersonNode> p1Opt = targetPersonRepository.findByPersonId(personId1);
            Optional<TargetPersonNode> p2Opt = targetPersonRepository.findByPersonId(personId2);
            if (p1Opt.isEmpty() || p2Opt.isEmpty()) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "关联人员不存在");
            }
            TargetPersonNode p1 = p1Opt.get();
            TargetPersonNode p2 = p2Opt.get();

            PersonRelationship rel = new PersonRelationship();
            rel.setTargetPerson(p2);
            rel.setRelationType("FREQUENT_CONTACT");
            rel.setRelationName("频繁联系人");
            rel.setContactCount(contactCount);
            rel.setStrength(strength);
            rel.setLastContactDate(LocalDate.now());
            p1.getFrequentContactRelations().add(rel);

            PersonRelationship reverse = new PersonRelationship();
            reverse.setTargetPerson(p1);
            reverse.setRelationType("FREQUENT_CONTACT");
            reverse.setRelationName("频繁联系人");
            reverse.setContactCount(contactCount);
            reverse.setStrength(strength);
            reverse.setLastContactDate(LocalDate.now());
            p2.getFrequentContactRelations().add(reverse);

            targetPersonRepository.save(p1);
            targetPersonRepository.save(p2);
            log.info("添加频繁联系人关系：{} <-> {}，联系次数：{}", personId1, personId2, contactCount);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("添加频繁联系人关系失败（Neo4j可能未启动）：{}", e.getMessage());
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
            log.warn("从Neo4j获取关系图谱失败，使用默认关系：{}", e.getMessage());
            List<TargetPerson> mockRelated = findMockRelated(personId);
            for (TargetPerson r : mockRelated) {
                Map<String, Object> node = new HashMap<>();
                node.put("id", r.getPersonId());
                node.put("label", r.getPersonName());
                node.put("type", r.getPersonType());
                node.put("level", r.getControlLevel());
                nodes.add(node);

                Map<String, Object> edge = new HashMap<>();
                edge.put("source", personId);
                edge.put("target", r.getPersonId());
                edge.put("relation", Math.random() > 0.5 ? "同案人员" : "频繁联系");
                edge.put("color", Math.random() > 0.5 ? "#ff4d4f" : "#faad14");
                edges.add(edge);
            }
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

    private List<TargetPerson> findMockRelated(String personId) {
        LambdaQueryWrapper<TargetPerson> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(TargetPerson::getPersonId, personId)
                .last("LIMIT 8");
        return targetPersonMapper.selectList(wrapper);
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
