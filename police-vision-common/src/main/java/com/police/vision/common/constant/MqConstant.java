package com.police.vision.common.constant;

public class MqConstant {

    public static final String ALARM_TOPIC = "police-alarm-topic";
    public static final String ALARM_DISPATCH_GROUP = "police-alarm-dispatch-group";
    public static final String ALARM_NOTIFY_CONSUMER_GROUP = "police-alarm-notify-group";

    public static final String VIDEO_ANALYSIS_TOPIC = "police-video-analysis-topic";
    public static final String VIDEO_ANALYSIS_GROUP = "police-video-analysis-group";
    public static final String VIDEO_ALARM_GROUP = "police-video-alarm-group";

    public static final String FACE_RECOGNITION_TOPIC = "police-face-recognition-topic";
    public static final String PLATE_RECOGNITION_TOPIC = "police-plate-recognition-topic";
    public static final String BEHAVIOR_ANALYSIS_TOPIC = "police-behavior-analysis-topic";

    public static final String ALARM_AGGREGATION_TOPIC = "police-alarm-aggregation-topic";
    public static final String REAL_TIME_STAT_TOPIC = "police-real-time-stat-topic";

    public static final String WEBSOCKET_PUSH_TOPIC = "police-websocket-push-topic";
    public static final String WEBSOCKET_PUSH_GROUP = "police-websocket-push-group";

    public static final String VIDEO_ALERT_TOPIC = "police-video-alert-topic";

    public static final String GIS_LOCATION_TOPIC = "police-gis-location-topic";
    public static final String GIS_LOCATION_GROUP = "police-gis-location-group";

    public static final String CONTROL_TOPIC = "police-control-topic";
    public static final String CONTROL_CONSUMER_GROUP = "police-control-consumer-group";

    public static final String DISPATCH_TOPIC = "police-dispatch-topic";
    public static final String DISPATCH_NOTIFY_TOPIC = "police-dispatch-notify-topic";
    public static final String TAG_AUTO_DISPATCH = "auto_dispatch";
    public static final String TAG_MANUAL_DISPATCH = "manual_dispatch";
    public static final String TAG_DISPATCH_RESULT = "dispatch_result";
    public static final String TAG_DISPATCH_TIMEOUT = "dispatch_timeout";
    public static final String TAG_DISPATCH_POLICE_PREFIX = "police_";

    public static final String TAG_FACE_MATCH = "face_match";
    public static final String TAG_AGGREGATION_ALERT = "aggregation_alert";
    public static final String TAG_FENCE_ALERT = "fence_alert";
    public static final String TAG_VISITOR_PUSH = "visitor_push";

    public static final String TAG_DISPATCH = "dispatch";
    public static final String TAG_NOTIFY = "notify";
    public static final String TAG_ALARM = "alarm";
    public static final String TAG_FACE = "face";
    public static final String TAG_PLATE = "plate";
    public static final String TAG_BEHAVIOR = "behavior";
    public static final String TAG_SCREEN = "screen";
    public static final String TAG_TRAFFIC = "traffic";
    public static final String TAG_VEHICLE_CONTROL = "vehicle_control";
    public static final String TAG_VEHICLE_FOLLOW = "vehicle_follow";
    public static final String TAG_VEHICLE_NIGHT = "vehicle_night";

    public static final String KAFKA_TOPIC_TRAFFIC_CAPTURE = "traffic-capture-topic";
    public static final String KAFKA_TOPIC_VEHICLE_CONTROL_ALERT = "vehicle-control-alert-topic";
    public static final String KAFKA_TOPIC_VEHICLE_TRACK = "vehicle-track-topic";
    public static final String KAFKA_CONSUMER_GROUP_TRAFFIC = "traffic-capture-consumer-group";
    public static final String KAFKA_CONSUMER_GROUP_CONTROL = "vehicle-control-consumer-group";
    public static final String KAFKA_CONSUMER_GROUP_TRACK = "vehicle-track-consumer-group";

    public static final String EVENT_TRAFFIC_DATA_TOPIC = "police-event-traffic-data-topic";
    public static final String EVENT_TRAFFIC_ALERT_TOPIC = "police-event-traffic-alert-topic";
    public static final String EVENT_TRAFFIC_DATA_GROUP = "police-event-traffic-data-group";
    public static final String EVENT_TRAFFIC_ALERT_GROUP = "police-event-traffic-alert-group";
    public static final String TAG_EVENT_TRAFFIC_DATA = "event_traffic_data";
    public static final String TAG_EVENT_TRAFFIC_ALERT = "event_traffic_alert";

    public static final String EVENT_MONITOR_STATUS_KEY = "event:monitor:status:";
}
