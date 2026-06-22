package com.police.vision.event.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.police.vision.event.config.EventNacosConfig;
import com.police.vision.event.enums.EmergencyPlanTemplateEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyPlanConfigService {

    private final Environment environment;
    private final EventNacosConfig nacosConfig;

    private static final String CONFIG_PREFIX = "emergency.plan.config.";

    public Map<String, Object> getPlanTemplateConfig(String templateCode) {
        Map<String, Object> config = new HashMap<>();
        config.put("templateCode", templateCode);
        config.put("steps", getPlanSteps(templateCode));
        config.put("requiredResources", getRequiredResources(templateCode));
        config.put("commandTemplates", getCommandTemplates(templateCode));
        return config;
    }

    public List<Map<String, Object>> getPlanSteps(String templateCode) {
        String key = CONFIG_PREFIX + templateCode + ".steps";
        String stepsJson = getConfigValue(key, "[]");
        try {
            JSONArray arr = JSON.parseArray(stepsJson);
            List<Map<String, Object>> steps = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Map<String, Object> step = new HashMap<>();
                step.put("stepNo", obj.getIntValue("stepNo", i + 1));
                step.put("stepName", obj.getString("stepName", "步骤" + (i + 1)));
                step.put("description", obj.getString("description", ""));
                step.put("responsibleDept", obj.getString("responsibleDept", "指挥中心"));
                step.put("deadlineMinutes", obj.getIntValue("deadlineMinutes", 30));
                step.put("required", obj.getBooleanValue("required", true));
                steps.add(step);
            }
            if (!steps.isEmpty()) {
                return steps;
            }
        } catch (Exception e) {
            log.warn("解析预案步骤配置失败，使用默认步骤，模板：{}", templateCode, e);
        }
        return getDefaultSteps(templateCode);
    }

    public Map<String, Object> getRequiredResources(String templateCode) {
        String key = CONFIG_PREFIX + templateCode + ".resources";
        String resourcesJson = getConfigValue(key, "{}");
        try {
            JSONObject obj = JSON.parseObject(resourcesJson);
            if (obj != null && !obj.isEmpty()) {
                Map<String, Object> resources = new HashMap<>();
                resources.put("policeCount", obj.getIntValue("policeCount", 20));
                resources.put("cameraCount", obj.getIntValue("cameraCount", 10));
                resources.put("supplyTypes", obj.getJSONArray("supplyTypes") != null
                        ? obj.getJSONArray("supplyTypes").toList(String.class) : null);
                resources.put("specialEquipments", obj.getJSONArray("specialEquipments") != null
                        ? obj.getJSONArray("specialEquipments").toList(String.class) : null);
                resources.put("radiusMeters", obj.getIntValue("radiusMeters", 500));
                return resources;
            }
        } catch (Exception e) {
            log.warn("解析预案资源配置失败，使用默认资源，模板：{}", templateCode, e);
        }
        return getDefaultResources(templateCode);
    }

    public List<Map<String, Object>> getCommandTemplates(String templateCode) {
        String key = CONFIG_PREFIX + templateCode + ".commandTemplates";
        String cmdJson = getConfigValue(key, "[]");
        try {
            JSONArray arr = JSON.parseArray(cmdJson);
            if (arr != null && !arr.isEmpty()) {
                List<Map<String, Object>> cmds = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Map<String, Object> cmd = new HashMap<>();
                    cmd.put("commandTitle", obj.getString("commandTitle", ""));
                    cmd.put("commandContent", obj.getString("commandContent", ""));
                    cmd.put("priority", obj.getIntValue("priority", 3));
                    cmd.put("deadlineMinutes", obj.getIntValue("deadlineMinutes", 60));
                    cmds.add(cmd);
                }
                return cmds;
            }
        } catch (Exception e) {
            log.warn("解析预案指令模板失败，模板：{}", templateCode, e);
        }
        return Collections.emptyList();
    }

    private String getConfigValue(String key, String defaultValue) {
        try {
            String value = environment.getProperty(key);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private List<Map<String, Object>> getDefaultSteps(String templateCode) {
        EmergencyPlanTemplateEnum template = EmergencyPlanTemplateEnum.getByCode(templateCode);
        List<Map<String, Object>> steps = new ArrayList<>();

        steps.add(buildStep(1, "启动响应", "指挥中心接报，启动应急预案，成立现场指挥部", "指挥中心", 5));
        steps.add(buildStep(2, "警力集结", "通知周边警力快速集结，赶赴事发现场", "一线指挥部", 10));
        steps.add(buildStep(3, "现场封控", "对事发区域实施分级封控，疏散无关人员", "辖区派出所", 15));
        steps.add(buildStep(4, "事态研判", "收集现场情报，评估事态等级，调整处置策略", "情报部门", 20));
        steps.add(buildStep(5, "专业处置", "专业力量开展处置行动（反恐/消防/医疗等）", "专业处置组", 60));
        steps.add(buildStep(6, "舆情管控", "监控舆情动态，发布官方信息，引导舆论走向", "宣传部门", 30));
        steps.add(buildStep(7, "善后恢复", "清理现场，恢复秩序，开展事故调查", "善后工作组", 120));

        if (template != null) {
            switch (template) {
                case TERRORISM:
                    steps.add(2, buildStep(3, "排爆作业", "排爆专家进场，排查爆炸物风险", "特警排爆队", 30));
                    steps.add(4, buildStep(5, "谈判介入", "谈判专家与嫌疑人沟通，稳定局势", "谈判专家组", 45));
                    break;
                case KIDNAPPING:
                    steps.add(3, buildStep(4, "人质安全评估", "评估人质状态，制定营救方案", "特警突击队", 25));
                    break;
                case FIRE:
                    steps.add(3, buildStep(4, "火灾扑救", "消防力量进场，控制火势蔓延", "消防救援大队", 45));
                    steps.add(5, buildStep(6, "危化品处置", "排查处理危险化学品泄漏风险", "环境监测组", 30));
                    break;
                case CROWD:
                    steps.add(3, buildStep(4, "人群分割", "对聚集人群实施分割隔离，防止事态扩大", "特警防暴队", 20));
                    break;
                case TRAFFIC:
                    steps.add(3, buildStep(4, "交通管制", "事故路段交通管制，开辟救援通道", "交通警察大队", 10));
                    steps.add(4, buildStep(5, "事故救援", "破拆救援，医疗救护，伤员转运", "医疗救护组", 30));
                    break;
                default:
                    break;
            }
        }

        return steps;
    }

    private Map<String, Object> getDefaultResources(String templateCode) {
        EmergencyPlanTemplateEnum template = EmergencyPlanTemplateEnum.getByCode(templateCode);
        Map<String, Object> resources = new HashMap<>();
        resources.put("radiusMeters", 500);

        if (template != null) {
            switch (template) {
                case TERRORISM:
                    resources.put("policeCount", 80);
                    resources.put("cameraCount", 30);
                    resources.put("supplyTypes", Arrays.asList("equipment", "communication", "medical"));
                    resources.put("specialEquipments", Arrays.asList("排爆机器人", "防弹盾牌", "狙击步枪", "夜视仪"));
                    break;
                case KIDNAPPING:
                    resources.put("policeCount", 50);
                    resources.put("cameraCount", 20);
                    resources.put("supplyTypes", Arrays.asList("equipment", "communication", "medical"));
                    resources.put("specialEquipments", Arrays.asList("窥视镜", "破门器", "防弹衣", "狙击枪"));
                    break;
                case FIRE:
                    resources.put("policeCount", 30);
                    resources.put("cameraCount", 15);
                    resources.put("supplyTypes", Arrays.asList("equipment", "medical", "logistics"));
                    resources.put("specialEquipments", Arrays.asList("灭火器", "空气呼吸器", "隔热服", "担架"));
                    break;
                case CROWD:
                    resources.put("policeCount", 100);
                    resources.put("cameraCount", 25);
                    resources.put("supplyTypes", Arrays.asList("equipment", "communication", "logistics"));
                    resources.put("specialEquipments", Arrays.asList("防暴盾牌", "催泪弹", "防暴头盔", "扩音设备"));
                    break;
                case TRAFFIC:
                    resources.put("policeCount", 20);
                    resources.put("cameraCount", 10);
                    resources.put("supplyTypes", Arrays.asList("equipment", "medical"));
                    resources.put("specialEquipments", Arrays.asList("破拆工具", "警示标志", "清障设备"));
                    break;
                case HEALTH:
                    resources.put("policeCount", 15);
                    resources.put("cameraCount", 8);
                    resources.put("radiusMeters", 2000);
                    resources.put("supplyTypes", Arrays.asList("medical", "logistics", "equipment"));
                    resources.put("specialEquipments", Arrays.asList("防护服", "检测试剂", "消毒设备", "救护车"));
                    break;
                default:
                    resources.put("policeCount", 30);
                    resources.put("cameraCount", 15);
                    resources.put("supplyTypes", Arrays.asList("equipment", "communication", "medical"));
                    break;
            }
        } else {
            resources.put("policeCount", 30);
            resources.put("cameraCount", 15);
            resources.put("supplyTypes", Arrays.asList("equipment", "communication", "medical"));
        }

        return resources;
    }

    private Map<String, Object> buildStep(int stepNo, String stepName, String description,
                                          String responsibleDept, int deadlineMinutes) {
        Map<String, Object> step = new HashMap<>();
        step.put("stepNo", stepNo);
        step.put("stepName", stepName);
        step.put("description", description);
        step.put("responsibleDept", responsibleDept);
        step.put("deadlineMinutes", deadlineMinutes);
        step.put("required", true);
        return step;
    }
}
