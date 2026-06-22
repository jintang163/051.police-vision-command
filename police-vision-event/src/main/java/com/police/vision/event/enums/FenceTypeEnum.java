package com.police.vision.event.enums;

import lombok.Getter;

@Getter
public enum FenceTypeEnum {

    BLOCKADE_ZONE("blockade", "封控区", "#ff4d4f"),
    CONTROL_ZONE("control", "管控区", "#faad14"),
    PREVENTION_ZONE("prevention", "防范区", "#52c41a"),
    ASSEMBLY_POINT("assembly", "集结点", "#1890ff"),
    CHECKPOINT("checkpoint", "检查点", "#722ed1");

    private final String code;
    private final String description;
    private final String color;

    FenceTypeEnum(String code, String description, String color) {
        this.code = code;
        this.description = description;
        this.color = color;
    }

    public static FenceTypeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (FenceTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
