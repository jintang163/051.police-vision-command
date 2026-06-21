package com.police.vision.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AlarmTypeEnum {

    THEFT(1, "盗窃", 3),
    FIGHT(2, "斗殴", 1),
    MISSING(3, "走失", 2),
    FIRE(4, "火警", 1),
    TRAFFIC_ACCIDENT(5, "交通事故", 2),
    DISTURBANCE(6, "扰民", 3),
    FRAUD(7, "诈骗", 2),
    DRUG(8, "涉毒", 1),
    EMERGENCY(9, "突发事件", 1),
    OTHER(99, "其他", 3);

    private final Integer code;
    private final String name;
    private final Integer priority;

    public static AlarmTypeEnum getByCode(Integer code) {
        if (code == null) {
            return OTHER;
        }
        for (AlarmTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return OTHER;
    }
}
