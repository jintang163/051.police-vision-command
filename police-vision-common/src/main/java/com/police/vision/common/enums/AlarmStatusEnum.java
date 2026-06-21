package com.police.vision.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AlarmStatusEnum {

    PENDING(0, "待派单"),
    DISPATCHED(1, "已派单"),
    ACCEPTED(2, "已接单"),
    ARRIVED(3, "已到达"),
    PROCESSING(4, "处置中"),
    COMPLETED(5, "已完成"),
    CANCELLED(6, "已取消"),
    ESCALATED(7, "已升级");

    private final Integer code;
    private final String name;

    public static AlarmStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AlarmStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean canTransitionTo(AlarmStatusEnum next) {
        return switch (this) {
            case PENDING -> next == DISPATCHED || next == CANCELLED;
            case DISPATCHED -> next == ACCEPTED || next == CANCELLED || next == ESCALATED;
            case ACCEPTED -> next == ARRIVED || next == CANCELLED;
            case ARRIVED -> next == PROCESSING || next == COMPLETED;
            case PROCESSING -> next == COMPLETED || next == ESCALATED;
            default -> false;
        };
    }
}
