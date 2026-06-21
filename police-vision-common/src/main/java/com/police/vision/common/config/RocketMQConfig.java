package com.police.vision.common.config;

import com.police.vision.common.constant.MqConstant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;

@Configuration
public class RocketMQConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new MappingJackson2MessageConverter();
    }

    @Bean
    public RocketMQTemplate rocketMQTemplate(org.apache.rocketmq.spring.core.RocketMQTemplate template) {
        template.setMessageConverter(messageConverter());
        return template;
    }

    public static String buildDestination(String topic, String tag) {
        return topic + ":" + tag;
    }

    public static String alarmDispatchDestination() {
        return buildDestination(MqConstant.ALARM_TOPIC, MqConstant.TAG_DISPATCH);
    }

    public static String alarmNotifyDestination() {
        return buildDestination(MqConstant.ALARM_TOPIC, MqConstant.TAG_NOTIFY);
    }

    public static String videoAlertDestination() {
        return buildDestination(MqConstant.VIDEO_ANALYSIS_TOPIC, MqConstant.TAG_ALARM);
    }

    public static String websocketScreenDestination() {
        return buildDestination(MqConstant.WEBSOCKET_PUSH_TOPIC, MqConstant.TAG_SCREEN);
    }
}
