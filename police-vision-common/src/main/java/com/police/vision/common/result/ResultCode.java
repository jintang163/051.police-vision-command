package com.police.vision.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "权限不足，禁止访问"),
    NOT_FOUND(404, "资源不存在"),

    PARAM_ERROR(1001, "参数校验失败"),
    PARAM_NULL(1002, "参数不能为空"),
    PARAM_ILLEGAL(1003, "参数不合法"),

    USER_NOT_EXIST(2001, "用户不存在"),
    USER_PASSWORD_ERROR(2002, "用户名或密码错误"),
    USER_DISABLED(2003, "账号已被禁用"),
    USER_EXIST(2004, "用户已存在"),
    TOKEN_EXPIRED(2005, "Token已过期"),
    TOKEN_INVALID(2006, "Token无效"),

    ALARM_NOT_EXIST(3001, "警情不存在"),
    ALARM_STATUS_ERROR(3002, "警情状态异常"),
    DISPATCH_FAILED(3003, "派单失败"),

    VIDEO_NOT_EXIST(4001, "视频不存在"),
    CAMERA_OFFLINE(4002, "摄像头离线"),
    AI_ANALYZE_FAILED(4003, "AI分析失败"),

    DATABASE_ERROR(5001, "数据库操作失败"),
    REDIS_ERROR(5002, "Redis操作失败"),
    MQ_ERROR(5003, "消息队列操作失败"),
    MINIO_ERROR(5004, "文件存储操作失败"),

    DISTRIBUTED_TRANSACTION_ERROR(6001, "分布式事务执行失败"),
    SERVICE_UNAVAILABLE(6002, "服务不可用"),
    RATE_LIMITED(6003, "请求过于频繁，请稍后再试");

    private final Integer code;
    private final String message;
}
