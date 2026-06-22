package com.police.vision.event.enums;

import lombok.Getter;

@Getter
public enum EmergencyPlanTemplateEnum {

    TERRORISM("terrorism", "暴恐袭击应急预案", 1,
            "恐怖袭击事件处置，包括人员疏散、封控、追捕、医疗救援等全流程",
            "TERROR_RESPONSE_LEVEL_1"),

    KIDNAPPING("kidnapping", "劫持人质应急预案", 2,
            "劫持人质事件处置，包括谈判、武力突击、医疗保障等流程",
            "KIDNAP_RESPONSE_LEVEL_1"),

    FIRE("fire", "火灾爆炸应急预案", 3,
            "火灾爆炸事件处置，包括人员疏散、灭火救援、交通管制等流程",
            "FIRE_RESPONSE_LEVEL_2"),

    MASS_INCIDENT("mass_incident", "群体性事件应急预案", 4,
            "群体性事件处置，包括现场疏导、舆情监控、隔离带设置等流程",
            "MASS_RESPONSE_LEVEL_2"),

    TRAFFIC_ACCIDENT("traffic_accident", "重大交通事故应急预案", 5,
            "重大交通事故处置，包括现场救援、交通疏导、事故调查等流程",
            "TRAFFIC_RESPONSE_LEVEL_2"),

    PUBLIC_HEALTH("public_health", "公共卫生事件应急预案", 6,
            "公共卫生事件处置，包括人员隔离、消杀、物资调配等流程",
            "HEALTH_RESPONSE_LEVEL_3"),

    NATURAL_DISASTER("natural_disaster", "自然灾害应急预案", 7,
            "自然灾害处置，包括人员转移、抢险救援、物资保障等流程",
            "DISASTER_RESPONSE_LEVEL_2"),

    CROWD_STAMPEDE("crowd_stampede", "人群踩踏应急预案", 8,
            "人群踩踏事件处置，包括紧急疏散、医疗救援、现场管控等流程",
            "STAMPEDE_RESPONSE_LEVEL_1");

    private final String code;
    private final String name;
    private final Integer priority;
    private final String description;
    private final String nacosConfigKey;

    EmergencyPlanTemplateEnum(String code, String name, Integer priority,
                               String description, String nacosConfigKey) {
        this.code = code;
        this.name = name;
        this.priority = priority;
        this.description = description;
        this.nacosConfigKey = nacosConfigKey;
    }

    public static EmergencyPlanTemplateEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (EmergencyPlanTemplateEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
