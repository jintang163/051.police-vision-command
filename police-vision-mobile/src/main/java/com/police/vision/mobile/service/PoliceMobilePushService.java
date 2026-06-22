package com.police.vision.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.util.MqUtil;
import com.police.vision.mobile.entity.MobileUser;
import com.police.vision.mobile.mapper.MobileUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoliceMobilePushService {

    private final MobileUserMapper mobileUserMapper;
    private final MobileWebSocketHandler mobileWebSocketHandler;
    private final DispatchMobileService dispatchMobileService;
    private final MqUtil mqUtil;

    public boolean pushPredictionAlertToPolice(
            String alertId, String alertNo, String alertType, Integer alertLevel,
            String title, String content,
            String personId, String personName,
            BigDecimal longitude, BigDecimal latitude, String locationDesc,
            Double probability, String predictTime, String triggerReason,
            String policeStationCode) {

        List<MobileUser> targetOfficers = findTargetOfficers(policeStationCode, longitude, latitude);
        if (targetOfficers == null || targetOfficers.isEmpty()) {
            log.warn("没有找到可推送的在线警员：alertId={}, stationCode={}", alertId, policeStationCode);
            return false;
        }

        int pushCount = 0;
        for (MobileUser officer : targetOfficers) {
            try {
                Map<String, Object> pushData = buildPushData(
                        alertId, alertNo, alertType, alertLevel, title, content,
                        personId, personName, longitude, latitude, locationDesc,
                        probability, predictTime, triggerReason, officer
                );

                boolean wsPushed = mobileWebSocketHandler.sendMessageToUser(
                        officer.getUserId(), "prediction_alert", pushData);

                if (wsPushed) {
                    pushCount++;
                    dispatchMobileService.createMobileAlertRecord(officer.getUserId(), alertId, alertNo, title, content);
                } else {
                    sendPushNotification(officer, alertId, title, content);
                    pushCount++;
                }

                log.debug("预测预警推送至警员：officerId={}, officerName={}, alertId={}",
                        officer.getUserId(), officer.getRealName(), alertId);

            } catch (Exception e) {
                log.error("推送预测预警至警员失败：officerId={}, alertId={}",
                        officer.getUserId(), alertId, e);
            }
        }

        log.info("预测预警推送完成：alertId={}, targetCount={}, pushedCount={}",
                alertId, targetOfficers.size(), pushCount);
        return pushCount > 0;
    }

    private List<MobileUser> findTargetOfficers(String policeStationCode, BigDecimal longitude, BigDecimal latitude) {
        try {
            LambdaQueryWrapper<MobileUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MobileUser::getUserStatus, 1);
            wrapper.eq(MobileUser::getOnlineStatus, 1);
            if (policeStationCode != null && !policeStationCode.isEmpty()) {
                wrapper.eq(MobileUser::getPoliceStationCode, policeStationCode);
            }
            wrapper.last("limit 10");
            List<MobileUser> users = mobileUserMapper.selectList(wrapper);

            if ((users == null || users.isEmpty()) && longitude != null && latitude != null) {
                LambdaQueryWrapper<MobileUser> nearWrapper = new LambdaQueryWrapper<>();
                nearWrapper.eq(MobileUser::getUserStatus, 1);
                nearWrapper.eq(MobileUser::getOnlineStatus, 1);
                nearWrapper.isNotNull(MobileUser::getLastLongitude);
                nearWrapper.isNotNull(MobileUser::getLastLatitude);
                nearWrapper.last("limit 10");
                users = mobileUserMapper.selectList(nearWrapper);
            }

            return users;
        } catch (Exception e) {
            log.error("查询目标警员失败", e);
            return null;
        }
    }

    private Map<String, Object> buildPushData(
            String alertId, String alertNo, String alertType, Integer alertLevel,
            String title, String content,
            String personId, String personName,
            BigDecimal longitude, BigDecimal latitude, String locationDesc,
            Double probability, String predictTime, String triggerReason,
            MobileUser officer) {

        Map<String, Object> data = new HashMap<>();
        data.put("alertId", alertId);
        data.put("alertNo", alertNo);
        data.put("alertType", alertType);
        data.put("alertLevel", alertLevel);
        data.put("title", title);
        data.put("content", content);
        data.put("personId", personId);
        data.put("personName", personName);
        data.put("longitude", longitude);
        data.put("latitude", latitude);
        data.put("locationDesc", locationDesc);
        data.put("probability", probability);
        data.put("predictTime", predictTime);
        data.put("triggerReason", triggerReason);
        data.put("pushTime", LocalDateTime.now().toString());
        data.put("targetOfficerId", officer.getUserId());
        data.put("targetOfficerName", officer.getRealName());
        return data;
    }

    private void sendPushNotification(MobileUser officer, String alertId, String title, String content) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("platform", officer.getPlatform());
            notification.put("deviceToken", officer.getDeviceToken());
            notification.put("userId", officer.getUserId());
            notification.put("title", title);
            notification.put("body", content);
            notification.put("badge", 1);
            notification.put("extra", Map.of("type", "prediction_alert", "alertId", alertId));

            mqUtil.send(MqConstant.DISPATCH_NOTIFY_TOPIC + ":" + MqConstant.TAG_NOTIFY, notification);
        } catch (Exception e) {
            log.error("发送离线通知失败：userId={}", officer.getUserId(), e);
        }
    }
}
