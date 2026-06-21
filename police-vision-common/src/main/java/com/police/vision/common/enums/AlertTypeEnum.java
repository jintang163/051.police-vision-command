package com.police.vision.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AlertTypeEnum {

    FACE_MATCH(1, "重点人员人脸识别", 1),
    PLATE_MATCH(2, "重点车辆车牌识别", 1),
    FIGHT_DETECTED(3, "打架斗殴检测", 1),
    CROWD_GATHERING(4, "人群聚集检测", 2),
    FALL_DETECTED(5, "人员倒地检测", 2),
    TRESSPASSING(6, "区域入侵检测", 2),
    ABNORMAL_BEHAVIOR(7, "异常行为检测", 3),
    FIRE_DETECTED(8, "烟雾火灾检测", 1),
    FENCE_BREACH(9, "电子围栏告警", 1),
    AGGREGATION(10, "异常聚集告警", 2),
    VEHICLE_CONTROL(11, "车辆布控告警", 1),
    VEHICLE_FOLLOW(12, "跟车分析告警", 2),
    VEHICLE_NIGHT_ACTIVE(13, "昼伏夜出车辆告警", 2);

    private final Integer code;
    private final String name;
    private final Integer level;

    public static AlertTypeEnum getByCode(Integer code) {
        if (code == null) {
            return ABNORMAL_BEHAVIOR;
        }
        for (AlertTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return ABNORMAL_BEHAVIOR;
    }
}
