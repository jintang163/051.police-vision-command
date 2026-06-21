package com.police.vision.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

public class SnowflakeIdUtil {

    private static final long WORKER_ID = 1;
    private static final long DATACENTER_ID = 1;
    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(WORKER_ID, DATACENTER_ID);

    private SnowflakeIdUtil() {}

    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    public static String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }

    public static String generateOrderNo(String prefix) {
        return prefix + System.currentTimeMillis() + nextId() % 10000;
    }

    public static String generateAlarmNo() {
        return "ALM" + System.currentTimeMillis() + String.format("%04d", nextId() % 10000);
    }

    public static String generateDispatchNo() {
        return "DSP" + System.currentTimeMillis() + String.format("%04d", nextId() % 10000);
    }

    public static String generateTaskNo() {
        return "TSK" + System.currentTimeMillis() + String.format("%04d", nextId() % 10000);
    }
}
