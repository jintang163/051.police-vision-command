package com.police.vision.event.mq;

import com.police.vision.common.constant.MqConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.EMERGENCY_COMMAND_TOPIC,
        consumerGroup = MqConstant.EMERGENCY_COMMAND_BROADCAST_GROUP,
        messageModel = MessageModel.BROADCASTING,
        selectorExpression = MqConstant.TAG_COMMAND_DISPATCH + "||" +
                MqConstant.TAG_EMERGENCY_PLAN_START + "||" +
                MqConstant.TAG_FENCE_UPDATE
)
public class EmergencyBroadcastConsumer implements RocketMQListener<Map<String, Object>> {

    @Override
    public void onMessage(Map<String, Object> message) {
        String tag = (String) message.getOrDefault("tag", "unknown");
        Long eventId = message.get("eventId") != null ?
                Long.valueOf(String.valueOf(message.get("eventId"))) : null;

        switch (tag) {
            case MqConstant.TAG_COMMAND_DISPATCH:
                broadcastToAllUnits(message, eventId);
                break;
            case MqConstant.TAG_EMERGENCY_PLAN_START:
                broadcastPlanStart(message, eventId);
                break;
            case MqConstant.TAG_FENCE_UPDATE:
                broadcastFenceUpdate(message, eventId);
                break;
            default:
                break;
        }
    }

    private void broadcastToAllUnits(Map<String, Object> message, Long eventId) {
        Object commandId = message.get("commandId");
        String commandTitle = (String) message.get("commandTitle");
        Object priority = message.get("priority");

        log.info("【广播-参战单位】指令ID={}, 事件ID={}, 标题={}, 优先级={}",
                commandId, eventId, commandTitle, priority);

        notifyCommandCenter(message);
        notifyFrontlineOfficers(message);
        notifyMobileApp(message);
    }

    private void broadcastPlanStart(Map<String, Object> message, Long eventId) {
        String planName = (String) message.get("planName");
        log.info("【广播-预案启动】事件ID={}, 预案={}, 警力={}人, 摄像头={}个, 物资={}项",
                eventId, planName,
                message.get("policeCount"), message.get("cameraCount"), message.get("supplyCount"));
    }

    private void broadcastFenceUpdate(Map<String, Object> message, Long eventId) {
        Object fenceId = message.get("fenceId");
        String fenceName = (String) message.get("fenceName");
        Object action = message.get("action");
        String fenceType = (String) message.get("fenceType");

        log.info("【广播-封控区更新】事件ID={}, 封控区ID={}, 名称={}, 类型={}, 操作={}",
                eventId, fenceId, fenceName, fenceType, action);
    }

    private void notifyCommandCenter(Map<String, Object> message) {
        log.debug("通知指挥中心大屏：指令下达 - {}", message.get("commandTitle"));
    }

    private void notifyFrontlineOfficers(Map<String, Object> message) {
        log.debug("通知前线作战人员：指令 - {}", message.get("commandId"));
    }

    private void notifyMobileApp(Map<String, Object> message) {
        log.debug("推送App通知：所有参战单位App - 指令ID={}", message.get("commandId"));
    }
}
