package com.police.vision.event.mq;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.EMERGENCY_COMMAND_TOPIC,
        consumerGroup = MqConstant.EMERGENCY_COMMAND_GROUP,
        selectorExpression = MqConstant.TAG_COMMAND_DISPATCH + "||" +
                MqConstant.TAG_COMMAND_RECEIVE + "||" +
                MqConstant.TAG_COMMAND_FEEDBACK + "||" +
                MqConstant.TAG_COMMAND_COMPLETE + "||" +
                MqConstant.TAG_COMMAND_TIMEOUT + "||" +
                MqConstant.TAG_COMMAND_CANCEL + "||" +
                MqConstant.TAG_EMERGENCY_PLAN_START + "||" +
                MqConstant.TAG_EMERGENCY_PLAN_END + "||" +
                MqConstant.TAG_FENCE_UPDATE
)
public class EmergencyCommandConsumer implements RocketMQListener<Map<String, Object>> {

    @Override
    public void onMessage(Map<String, Object> message) {
        String tag = (String) message.getOrDefault("tag", "unknown");
        Long eventId = message.get("eventId") != null ?
                Long.valueOf(String.valueOf(message.get("eventId"))) : null;
        Object commandId = message.get("commandId");

        switch (tag) {
            case MqConstant.TAG_COMMAND_DISPATCH:
                handleCommandDispatch(message, commandId, eventId);
                break;
            case MqConstant.TAG_COMMAND_RECEIVE:
                log.info("【指令接收】指令ID={}，事件ID={}", commandId, eventId);
                break;
            case MqConstant.TAG_COMMAND_FEEDBACK:
                log.info("【指令反馈】指令ID={}，事件ID={}", commandId, eventId);
                break;
            case MqConstant.TAG_COMMAND_COMPLETE:
                log.info("【指令完成】指令ID={}，事件ID={}", commandId, eventId);
                handleCommandComplete(message, commandId, eventId);
                break;
            case MqConstant.TAG_COMMAND_TIMEOUT:
                log.warn("【指令超时】指令ID={}，事件ID={}，超时次数={}",
                        commandId, eventId, message.get("timeoutCount"));
                break;
            case MqConstant.TAG_COMMAND_CANCEL:
                log.info("【指令取消】指令ID={}，事件ID={}", commandId, eventId);
                break;
            case MqConstant.TAG_EMERGENCY_PLAN_START:
                handleEmergencyPlanStart(message, eventId);
                break;
            case MqConstant.TAG_EMERGENCY_PLAN_END:
                log.info("【预案结束】事件ID={}，预案ID={}", eventId, message.get("planId"));
                break;
            case MqConstant.TAG_FENCE_UPDATE:
                log.info("【封控区更新】事件ID={}，封控区ID={}，操作={}",
                        eventId, message.get("fenceId"), message.get("action"));
                break;
            default:
                log.debug("【应急消息】未处理的标签={}，消息={}", tag, JSON.toJSONString(message));
        }
    }

    private void handleCommandDispatch(Map<String, Object> message, Object commandId, Long eventId) {
        String commandTitle = (String) message.get("commandTitle");
        String commandContent = (String) message.get("commandContent");
        Object priority = message.get("priority");
        String senderName = (String) message.get("senderName");
        String receiverNames = (String) message.get("receiverNames");

        log.info("========================================================================");
        log.info("【指令下达】新的应急调度指令");
        log.info("------------------------------------------------------------------------");
        log.info("指令编号: {}", message.get("commandNo"));
        log.info("指令ID:   {}", commandId);
        log.info("事件ID:   {}", eventId);
        log.info("指令标题: {}", commandTitle);
        log.info("指令内容: {}", commandContent != null && commandContent.length() > 100 ?
                commandContent.substring(0, 100) + "..." : commandContent);
        log.info("优先级:   {} (1-紧急/2-高/3-普通/4-低)", priority);
        log.info("发送人:   {}", senderName);
        log.info("接收单位: {}", receiverNames);
        log.info("时限:     {} 分钟", message.get("deadlineMinutes"));
        log.info("下达时间: {}", message.get("dispatchTime"));
        log.info("========================================================================");

        pushToMobileNotification(message);
    }

    private void handleCommandComplete(Map<String, Object> message, Object commandId, Long eventId) {
        log.info("========================================================================");
        log.info("【指令完成闭环】");
        log.info("------------------------------------------------------------------------");
        log.info("指令ID:   {}", commandId);
        log.info("事件ID:   {}", eventId);
        log.info("反馈内容: {}", message.get("feedbackContent"));
        log.info("完成时间: {}", message.get("completeTime"));
        log.info("========================================================================");
    }

    private void handleEmergencyPlanStart(Map<String, Object> message, Long eventId) {
        log.info("========================================================================");
        log.info("【应急预案启动】一键应急响应触发");
        log.info("------------------------------------------------------------------------");
        log.info("事件ID:       {}", eventId);
        log.info("事件名称:     {}", message.get("eventName"));
        log.info("事件等级:     {}", message.get("eventLevel"));
        log.info("预案ID:       {}", message.get("planId"));
        log.info("预案名称:     {}", message.get("planName"));
        log.info("预案模板:     {}", message.get("templateCode"));
        log.info("应急等级:     {}", message.get("emergencyLevel"));
        log.info("资源半径:     {} 米", message.get("resourceRadius"));
        log.info("分配警力:     {} 人", message.get("policeCount"));
        log.info("分配摄像头:   {} 个", message.get("cameraCount"));
        log.info("分配物资:     {} 项", message.get("supplyCount"));
        log.info("视频会议室:   {}", message.get("videoRoomId"));
        log.info("启动时间:     {}", message.get("startTime"));
        log.info("========================================================================");
    }

    private void pushToMobileNotification(Map<String, Object> message) {
        log.debug("推送到移动端App通知栏，指令ID={}", message.get("commandId"));
    }
}
