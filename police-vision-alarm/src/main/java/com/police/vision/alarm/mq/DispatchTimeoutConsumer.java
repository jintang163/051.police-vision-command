package com.police.vision.alarm.mq;

import com.police.vision.alarm.service.DispatchService;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.enums.AlarmStatusEnum;
import com.police.vision.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.ALARM_TOPIC,
        consumerGroup = MqConstant.ALARM_DISPATCH_GROUP,
        selectorExpression = MqConstant.TAG_DISPATCH
)
public class DispatchTimeoutConsumer implements RocketMQListener<String> {

    private final DispatchService dispatchService;

    @Override
    public void onMessage(String message) {
        try {
            Long alarmId = Long.parseLong(message);
            log.info("收到派单超时消息，警情ID：{}", alarmId);

            var dispatchRecord = dispatchService.getDispatchRecord(alarmId);

            if (dispatchRecord != null && dispatchRecord.getDispatchStatus() != null
                    && dispatchRecord.getDispatchStatus() == 0) {

                dispatchService.escalateAlarm(alarmId, dispatchRecord.getCommanderId());

                log.warn("警情派单超时已自动升级，警情ID：{}", alarmId);
            } else {
                log.info("警情已响应或已处置，无需升级，警情ID：{}", alarmId);
            }
        } catch (Exception e) {
            log.error("处理派单超时消息失败，message：{}", message, e);
        }
    }
}
