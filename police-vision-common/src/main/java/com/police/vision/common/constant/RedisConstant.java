package com.police.vision.common.constant;

public class RedisConstant {

    public static final String TOKEN_PREFIX = "auth:token:";
    public static final String USER_PREFIX = "auth:user:";
    public static final Long TOKEN_EXPIRE = 7200L;

    public static final String POLICE_STATUS_PREFIX = "gis:police:status:";
    public static final String POLICE_LOCATION_PREFIX = "gis:police:location:";
    public static final String ALARM_HEATMAP_PREFIX = "gis:alarm:heatmap:";

    public static final String ALARM_DISPATCH_LOCK_PREFIX = "alarm:dispatch:lock:";
    public static final String ALARM_ONLINE_COUNT_PREFIX = "alarm:online:count:";

    public static final String VIDEO_CAMERA_STATUS_PREFIX = "video:camera:status:";
    public static final String FACE_FEATURE_PREFIX = "video:face:feature:";
    public static final String PLATE_FEATURE_PREFIX = "video:plate:feature:";
    public static final String TARGET_PERSON_KEY = "video:target:person:";
    public static final String TARGET_VEHICLE_KEY = "video:target:vehicle:";

    public static final String WEBSOCKET_USER_PREFIX = "ws:user:";
    public static final String WEBSOCKET_SESSION_PREFIX = "ws:session:";

    public static final String LOCK_PREFIX = "lock:";
    public static final Long LOCK_EXPIRE = 30L;

    public static final Long ONLINE_STATUS_EXPIRE = 60L;
    public static final Long LOCATION_EXPIRE = 300L;

    public static final String RATE_LIMIT_PREFIX = "rate:limit:";
}
