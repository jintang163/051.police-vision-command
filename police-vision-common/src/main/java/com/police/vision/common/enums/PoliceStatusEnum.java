package com.police.vision.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PoliceStatusEnum {

    OFFLINE(0, "离线"),
    IDLE(1, "空闲"),
    ON_PATROL(2, "巡逻中"),
    ON_DUTY(3, "出警中"),
    ON_BREAK(4, "休息中"),
    UNAVAILABLE(5, "不可用");

    private final Integer code;
    private final String name;

    public static PoliceStatusEnum getByCode(Integer code) {
        if (code == null) {
            return OFFLINE;
        }
        for (PoliceStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return OFFLINE;
    }

    public boolean isAvailable() {
        return this == IDLE || this == ON_PATROL;
    }
}
