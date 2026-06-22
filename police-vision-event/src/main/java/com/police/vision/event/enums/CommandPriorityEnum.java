package com.police.vision.event.enums;

import lombok.Getter;

@Getter
public enum CommandPriorityEnum {

    URGENT(1, "紧急", "#ff4d4f"),
    HIGH(2, "高", "#faad14"),
    NORMAL(3, "普通", "#1890ff"),
    LOW(4, "低", "#52c41a");

    private final Integer code;
    private final String description;
    private final String color;

    CommandPriorityEnum(Integer code, String description, String color) {
        this.code = code;
        this.description = description;
        this.color = color;
    }

    public static CommandPriorityEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CommandPriorityEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
