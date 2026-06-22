package com.police.vision.event.enums;

import lombok.Getter;

@Getter
public enum CommandStatusEnum {

    CREATED(0, "已创建"),
    DISPATCHED(1, "已下达"),
    RECEIVED(2, "已接收"),
    EXECUTING(3, "执行中"),
    FEEDBACK(4, "已反馈"),
    COMPLETED(5, "已完成"),
    CANCELLED(6, "已取消"),
    TIMEOUT(7, "已超时");

    private final Integer code;
    private final String description;

    CommandStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static CommandStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CommandStatusEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }

    public static String getDescByCode(Integer code) {
        CommandStatusEnum e = getByCode(code);
        return e != null ? e.getDescription() : "未知状态";
    }
}
