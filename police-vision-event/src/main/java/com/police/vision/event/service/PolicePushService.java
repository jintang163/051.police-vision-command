package com.police.vision.event.service;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.util.MqUtil;
import com.police.vision.event.config.EventNacosConfig;
import com.police.vision.event.dto.CommandReceiptDTO;
import com.police.vision.event.entity.SecEmergencyCommand;
import com.police.vision.event.enums.CommandPriorityEnum;
import com.police.vision.event.enums.CommandStatusEnum;
import com.police.vision.event.mapper.SecEmergencyCommandMapper;
import com.police.vision.event.util.CommandStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicePushService {

    private final SecEmergencyCommandMapper commandMapper;
    private final MqUtil mqUtil;
    private final EventNacosConfig nacosConfig;

    @Value("${emergency.push.enabled:true}")
    private boolean pushEnabled;

    @Value("${emergency.push.retry-times:3}")
    private int pushRetryTimes;

    @Value("${emergency.push.channel:mq,app,sms}")
    private String pushChannels;

    public Map<String, Object> pushCommandToPolice(SecEmergencyCommand command) {
        if (!pushEnabled) {
            log.info("警务端推送已禁用，跳过推送，指令ID：{}", command.getId());
            return buildPushResult(false, "PUSH_DISABLED", "推送功能已禁用");
        }

        log.info("开始向警务端推送指令，指令ID：{}，通道：{}", command.getId(), pushChannels);

        Map<String, Object> pushResult = new HashMap<>();
        pushResult.put("commandId", command.getId());
        pushResult.put("commandNo", command.getCommandNo());
        pushResult.put("pushTime", System.currentTimeMillis());

        List<Map<String, Object>> channelResults = new ArrayList<>();
        String[] channels = pushChannels.split(",");
        for (String channel : channels) {
            channel = channel.trim();
            Map<String, Object> channelResult = pushByChannel(channel, command);
            channelResults.add(channelResult);
        }

        pushResult.put("channels", channelResults);
        pushResult.put("totalChannels", channels.length);
        pushResult.put("successChannels", channelResults.stream().filter(c -> Boolean.TRUE.equals(c.get("success"))).count());
        pushResult.put("pushEnabled", pushEnabled);

        log.info("警务端推送完成，指令ID：{}，成功通道：{}/{}",
                command.getId(), pushResult.get("successChannels"), pushResult.get("totalChannels"));

        return pushResult;
    }

    private Map<String, Object> pushByChannel(String channel, SecEmergencyCommand command) {
        Map<String, Object> result = new HashMap<>();
        result.put("channel", channel);
        result.put("commandId", command.getId());

        try {
            switch (channel) {
                case "mq":
                    pushViaMq(command);
                    result.put("success", true);
                    result.put("message", "MQ广播推送成功");
                    break;
                case "app":
                    pushViaApp(command);
                    result.put("success", true);
                    result.put("message", "警务App推送成功");
                    break;
                case "sms":
                    pushViaSms(command);
                    result.put("success", true);
                    result.put("message", "短信通知成功（模拟）");
                    break;
                default:
                    result.put("success", false);
                    result.put("message", "未知推送通道：" + channel);
            }
        } catch (Exception e) {
            log.error("推送失败，通道：{}，指令ID：{}", channel, command.getId(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private void pushViaMq(SecEmergencyCommand command) {
        Map<String, Object> message = buildPushMessage(command, "mq");
        mqUtil.sendBroadcast(
                RocketMQConfig.buildDestination(MqConstant.EMERGENCY_COMMAND_TOPIC, MqConstant.TAG_COMMAND_DISPATCH),
                message
        );
        log.debug("通过MQ广播推送指令，指令ID：{}", command.getId());
    }

    private void pushViaApp(SecEmergencyCommand command) {
        Map<String, Object> pushPayload = new HashMap<>();
        pushPayload.put("type", "EMERGENCY_COMMAND");
        pushPayload.put("title", buildPushTitle(command));
        pushPayload.put("content", truncate(command.getCommandContent(), 100));
        pushPayload.put("commandId", command.getId());
        pushPayload.put("commandNo", command.getCommandNo());
        pushPayload.put("eventId", command.getEventId());
        pushPayload.put("priority", command.getPriority());
        pushPayload.put("deadlineMinutes", command.getDeadlineMinutes());
        pushPayload.put("senderName", command.getSenderName());
        pushPayload.put("dispatchTime", command.getDispatchTime() != null
                ? command.getDispatchTime().toString() : null);
        pushPayload.put("timestamp", System.currentTimeMillis());

        mqUtil.sendBroadcast(
                "PUSH_NOTIFICATION_TOPIC:emergency_command",
                pushPayload
        );

        log.debug("通过警务App推送指令，指令ID：{}，标题：{}", command.getId(), buildPushTitle(command));
    }

    private void pushViaSms(SecEmergencyCommand command) {
        log.info("短信推送（模拟），指令ID：{}，优先级：{}", command.getId(), command.getPriority());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> processCommandReceipt(CommandReceiptDTO receipt) {
        log.info("处理警务端指令回执，指令ID：{}，回执类型：{}，操作人：{}",
                receipt.getCommandId(), receipt.getReceiptType(), receipt.getOperatorName());

        SecEmergencyCommand command = commandMapper.selectById(receipt.getCommandId());
        if (command == null) {
            throw new IllegalArgumentException("指令不存在，ID：" + receipt.getCommandId());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("commandId", receipt.getCommandId());
        result.put("commandNo", command.getCommandNo());
        result.put("receiptType", receipt.getReceiptType());
        result.put("processed", true);
        result.put("processTime", System.currentTimeMillis());

        CommandStatusEnum targetStatus = mapReceiptToStatus(receipt.getReceiptType());
        if (targetStatus == null) {
            result.put("warning", "未知回执类型：" + receipt.getReceiptType());
            return result;
        }

        int oldStatus = command.getStatus();
        int newStatus = targetStatus.getCode();

        if (!CommandStateMachine.canTransition(oldStatus, newStatus)) {
            log.warn("回执导致非法状态流转，指令ID：{}，{} → {}",
                    receipt.getCommandId(), oldStatus, newStatus);
            result.put("skipped", true);
            result.put("reason", "状态不允许从 " + oldStatus + " 流转到 " + newStatus);
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        switch (targetStatus) {
            case RECEIVED:
                command.setReceiveTime(now);
                break;
            case EXECUTING:
                command.setExecuteStartTime(now);
                break;
            case FEEDBACK:
                command.setFeedbackTime(now);
                command.setFeedbackContent(receipt.getFeedbackContent());
                break;
            case COMPLETED:
                command.setCompleteTime(now);
                command.setFeedbackContent(receipt.getFeedbackContent());
                break;
            case TIMEOUT:
                command.setTimeoutCount(command.getTimeoutCount() + 1);
                break;
            default:
                break;
        }

        command.setStatus(newStatus);
        command.setUpdateTime(now);
        commandMapper.updateById(command);

        sendReceiptAck(receipt, command);

        log.info("指令回执处理成功，指令ID：{}，状态：{} → {}", receipt.getCommandId(), oldStatus, newStatus);
        result.put("oldStatus", oldStatus);
        result.put("newStatus", newStatus);
        result.put("success", true);
        return result;
    }

    private CommandStatusEnum mapReceiptToStatus(String receiptType) {
        if (receiptType == null) return null;
        switch (receiptType.toUpperCase()) {
            case "RECEIVE":
            case "RECEIVED":
                return CommandStatusEnum.RECEIVED;
            case "EXECUTE":
            case "EXECUTING":
            case "START":
                return CommandStatusEnum.EXECUTING;
            case "FEEDBACK":
                return CommandStatusEnum.FEEDBACK;
            case "COMPLETE":
            case "COMPLETED":
            case "FINISH":
                return CommandStatusEnum.COMPLETED;
            case "TIMEOUT":
                return CommandStatusEnum.TIMEOUT;
            case "CANCEL":
                return CommandStatusEnum.CANCELLED;
            default:
                return null;
        }
    }

    private void sendReceiptAck(CommandReceiptDTO receipt, SecEmergencyCommand command) {
        Map<String, Object> ackMessage = new HashMap<>();
        ackMessage.put("type", "COMMAND_RECEIPT_ACK");
        ackMessage.put("commandId", receipt.getCommandId());
        ackMessage.put("commandNo", command.getCommandNo());
        ackMessage.put("receiptType", receipt.getReceiptType());
        ackMessage.put("newStatus", command.getStatus());
        ackMessage.put("operatorId", receipt.getOperatorId());
        ackMessage.put("operatorName", receipt.getOperatorName());
        ackMessage.put("timestamp", System.currentTimeMillis());

        mqUtil.sendBroadcast(
                "PUSH_NOTIFICATION_TOPIC:command_receipt_ack",
                ackMessage
        );
    }

    private String buildPushTitle(SecEmergencyCommand command) {
        CommandPriorityEnum priority = CommandPriorityEnum.getByCode(command.getPriority());
        String prefix = priority != null ? "【" + priority.getName() + "指令】" : "【应急指令】";
        return prefix + command.getCommandTitle();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private Map<String, Object> buildPushResult(boolean success, String code, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("code", code);
        result.put("message", message);
        return result;
    }

    @Async
    public void asyncPushCommand(SecEmergencyCommand command) {
        try {
            pushCommandToPolice(command);
        } catch (Exception e) {
            log.error("异步推送指令异常，指令ID：{}", command.getId(), e);
        }
    }
}
