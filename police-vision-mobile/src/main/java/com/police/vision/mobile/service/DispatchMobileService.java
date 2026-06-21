package com.police.vision.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.dto.AlarmStatusUpdateDTO;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.enums.AlarmStatusEnum;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.mobile.entity.MobileDispatchRecord;
import com.police.vision.mobile.entity.PoliceStatusLog;
import com.police.vision.mobile.handler.MobileWebSocketHandler;
import com.police.vision.mobile.mapper.MobileDispatchRecordMapper;
import com.police.vision.mobile.mapper.PoliceStatusLogMapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchMobileService {

    private final MobileDispatchRecordMapper mobileDispatchRecordMapper;
    private final PoliceStatusLogMapper policeStatusLogMapper;
    private final MobileWebSocketHandler mobileWebSocketHandler;
    private final MqUtil mqUtil;
    private final RedisUtil redisUtil;

    @Transactional(rollbackFor = Exception.class)
    public void acceptDispatch(Long policeId, Long dispatchId) {
        MobileDispatchRecord record = getMobileDispatchRecord(dispatchId, policeId);
        if (record == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "派单记录不存在");
        }
        if (!policeId.equals(record.getPoliceId())) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "无权处理此派单");
        }
        if (record.getDispatchStatus() >= 1) {
            throw new BusinessException(ResultCode.OPERATION_ERROR, "派单已处理");
        }

        record.setDispatchStatus(1);
        record.setResponseTime(LocalDateTime.now());
        mobileDispatchRecordMapper.updateById(record);

        updateAlarmStatus(record.getAlarmId(), AlarmStatusEnum.DISPATCHED.getCode(),
                null, null, "警员已出警");

        saveStatusLog(policeId, dispatchId, 1, "出警", null);

        Map<String, Object> statusMsg = buildDispatchStatus(record);
        mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("dispatch_status", statusMsg));

        log.info("警员接受派单：policeId={}, dispatchId={}", policeId, dispatchId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void arriveDispatch(Long policeId, Long dispatchId, BigDecimal longitude, BigDecimal latitude) {
        MobileDispatchRecord record = getMobileDispatchRecord(dispatchId, policeId);
        if (record == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "派单记录不存在");
        }
        if (!policeId.equals(record.getPoliceId())) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "无权处理此派单");
        }
        if (record.getDispatchStatus() < 1) {
            throw new BusinessException(ResultCode.OPERATION_ERROR, "请先接受派单");
        }
        if (record.getDispatchStatus() >= 2) {
            throw new BusinessException(ResultCode.OPERATION_ERROR, "已确认到达现场");
        }

        record.setDispatchStatus(2);
        record.setArriveTime(LocalDateTime.now());
        record.setArriveLongitude(longitude);
        record.setArriveLatitude(latitude);
        mobileDispatchRecordMapper.updateById(record);

        updateAlarmStatus(record.getAlarmId(), AlarmStatusEnum.ARRIVED.getCode(),
                longitude, latitude, "警员已到达现场");

        saveStatusLog(policeId, dispatchId, 2, "到达现场",
                "经度：" + longitude + "，纬度：" + latitude);

        Map<String, Object> statusMsg = buildDispatchStatus(record);
        mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("dispatch_status", statusMsg));

        log.info("警员到达现场：policeId={}, dispatchId={}", policeId, dispatchId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeDispatch(Long policeId, Long dispatchId, String result, String remark) {
        MobileDispatchRecord record = getMobileDispatchRecord(dispatchId, policeId);
        if (record == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "派单记录不存在");
        }
        if (!policeId.equals(record.getPoliceId())) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "无权处理此派单");
        }
        if (record.getDispatchStatus() < 2) {
            throw new BusinessException(ResultCode.OPERATION_ERROR, "请先确认到达现场");
        }
        if (record.getDispatchStatus() >= 3) {
            throw new BusinessException(ResultCode.OPERATION_ERROR, "派单已完成");
        }

        record.setDispatchStatus(3);
        record.setFinishTime(LocalDateTime.now());
        record.setHandleResult(result);
        record.setHandleRemark(remark);
        mobileDispatchRecordMapper.updateById(record);

        updateAlarmStatus(record.getAlarmId(), AlarmStatusEnum.COMPLETED.getCode(),
                null, null, result);

        saveStatusLog(policeId, dispatchId, 3, "处理完成",
                "处理结果：" + result + "，备注：" + remark);

        Map<String, Object> statusMsg = buildDispatchStatus(record);
        mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("dispatch_status", statusMsg));

        log.info("警员完成派单：policeId={}, dispatchId={}, result={}", policeId, dispatchId, result);
    }

    public List<MobileDispatchRecord> getMyDispatches(Long policeId, Integer status) {
        LambdaQueryWrapper<MobileDispatchRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MobileDispatchRecord::getPoliceId, policeId);
        if (status != null) {
            wrapper.eq(MobileDispatchRecord::getDispatchStatus, status);
        }
        wrapper.orderByDesc(MobileDispatchRecord::getCreateTime);
        List<MobileDispatchRecord> records = mobileDispatchRecordMapper.selectList(wrapper);

        for (MobileDispatchRecord record : records) {
            enrichDispatchInfo(record);
        }
        return records;
    }

    public MobileDispatchRecord getDispatchDetail(Long policeId, Long dispatchId) {
        MobileDispatchRecord record = getMobileDispatchRecord(dispatchId, policeId);
        if (record == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "派单记录不存在");
        }
        if (!policeId.equals(record.getPoliceId())) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "无权查看此派单");
        }
        enrichDispatchInfo(record);
        return record;
    }

    public void sendPendingDispatches(Long policeId) {
        LambdaQueryWrapper<MobileDispatchRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MobileDispatchRecord::getPoliceId, policeId)
                .in(MobileDispatchRecord::getDispatchStatus, 0, 1, 2)
                .orderByDesc(MobileDispatchRecord::getCreateTime);

        List<MobileDispatchRecord> records = mobileDispatchRecordMapper.selectList(wrapper);
        for (MobileDispatchRecord record : records) {
            enrichDispatchInfo(record);
            mobileWebSocketHandler.pushNewDispatch(policeId, record);
        }
        log.info("推送未完成派单给警员：policeId={}, count={}", policeId, records.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void createMobileDispatch(Long dispatchId, Long alarmId, Long policeId,
                                      String dispatchNo, String alarmNo, Integer priority,
                                      String alarmContent, String address,
                                      BigDecimal longitude, BigDecimal latitude,
                                      String dispatchRemark) {
        MobileDispatchRecord record = new MobileDispatchRecord();
        record.setDispatchId(dispatchId);
        record.setAlarmId(alarmId);
        record.setPoliceId(policeId);
        record.setDispatchNo(dispatchNo);
        record.setAlarmNo(alarmNo);
        record.setPriority(priority);
        record.setAlarmContent(alarmContent);
        record.setAddress(address);
        record.setLongitude(longitude);
        record.setLatitude(latitude);
        record.setDispatchRemark(dispatchRemark);
        record.setDispatchStatus(0);
        record.setDispatchTime(LocalDateTime.now());
        mobileDispatchRecordMapper.insert(record);

        enrichDispatchInfo(record);
        mobileWebSocketHandler.pushNewDispatch(policeId, record);

        log.info("创建移动端派单记录：dispatchId={}, policeId={}, alarmId={}",
                dispatchId, policeId, alarmId);
    }

    public Map<String, Object> calculateRoute(Long policeId, Long dispatchId) {
        MobileDispatchRecord record = getDispatchDetail(policeId, dispatchId);

        String policeLocationStr = redisUtil.get(RedisConstant.POLICE_LOCATION_PREFIX + policeId);
        BigDecimal startLongitude = null;
        BigDecimal startLatitude = null;

        if (policeLocationStr != null) {
            GpsLocation location = JSON.parseObject(policeLocationStr, GpsLocation.class);
            startLongitude = location.getLongitude();
            startLatitude = location.getLatitude();
        }

        Map<String, Object> routeInfo = new HashMap<>();
        routeInfo.put("dispatchId", dispatchId);
        routeInfo.put("startLongitude", startLongitude);
        routeInfo.put("startLatitude", startLatitude);
        routeInfo.put("endLongitude", record.getLongitude());
        routeInfo.put("endLatitude", record.getLatitude());
        routeInfo.put("address", record.getAddress());

        if (startLongitude != null && startLatitude != null) {
            double distance = calculateDistance(
                    startLatitude.doubleValue(), startLongitude.doubleValue(),
                    record.getLatitude().doubleValue(), record.getLongitude().doubleValue());
            routeInfo.put("distance", Math.round(distance * 100) / 100.0);

            int estimatedMinutes = (int) Math.ceil(distance / 60.0 * 60);
            routeInfo.put("estimatedMinutes", estimatedMinutes);
        }

        List<Map<String, Object>> waypoints = new ArrayList<>();
        Map<String, Object> start = new HashMap<>();
        start.put("longitude", startLongitude);
        start.put("latitude", startLatitude);
        waypoints.add(start);

        Map<String, Object> end = new HashMap<>();
        end.put("longitude", record.getLongitude());
        end.put("latitude", record.getLatitude());
        waypoints.add(end);

        routeInfo.put("waypoints", waypoints);

        return routeInfo;
    }

    private MobileDispatchRecord getMobileDispatchRecord(Long dispatchId, Long policeId) {
        LambdaQueryWrapper<MobileDispatchRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MobileDispatchRecord::getDispatchId, dispatchId)
                .eq(MobileDispatchRecord::getPoliceId, policeId);
        return mobileDispatchRecordMapper.selectOne(wrapper);
    }

    private void enrichDispatchInfo(MobileDispatchRecord record) {
    }

    private void updateAlarmStatus(Long alarmId, Integer status, BigDecimal longitude,
                                    BigDecimal latitude, String remark) {
        AlarmStatusUpdateDTO statusDTO = new AlarmStatusUpdateDTO();
        statusDTO.setAlarmId(alarmId);
        statusDTO.setStatus(status);
        statusDTO.setLongitude(longitude);
        statusDTO.setLatitude(latitude);
        statusDTO.setRemark(remark);

        mqUtil.send(MqConstant.ALARM_TOPIC + ":" + MqConstant.TAG_ALARM, statusDTO);
    }

    private void saveStatusLog(Long policeId, Long dispatchId, Integer status,
                                String statusName, String remark) {
        PoliceStatusLog log = new PoliceStatusLog();
        log.setPoliceId(policeId);
        log.setDispatchId(dispatchId);
        log.setStatus(status);
        log.setStatusName(statusName);
        log.setRemark(remark);
        log.setOperateTime(LocalDateTime.now());
        policeStatusLogMapper.insert(log);
    }

    private Map<String, Object> buildDispatchStatus(MobileDispatchRecord record) {
        Map<String, Object> status = new HashMap<>();
        status.put("dispatchId", record.getDispatchId());
        status.put("dispatchNo", record.getDispatchNo());
        status.put("alarmId", record.getAlarmId());
        status.put("policeId", record.getPoliceId());
        status.put("dispatchStatus", record.getDispatchStatus());
        status.put("updateTime", LocalDateTime.now());
        return status;
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * 6378.137;
        return s;
    }
}
