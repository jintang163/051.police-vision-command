package com.police.vision.event.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.event.config.SentinelConfig;
import com.police.vision.event.dto.EmergencyFenceCreateDTO;
import com.police.vision.event.entity.SecEmergencyFence;
import com.police.vision.event.entity.SecEvent;
import com.police.vision.event.enums.FenceTypeEnum;
import com.police.vision.event.mapper.SecEmergencyFenceMapper;
import com.police.vision.event.mapper.SecEventMapper;
import com.police.vision.event.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyFenceService {

    private final SecEmergencyFenceMapper fenceMapper;
    private final SecEventMapper eventMapper;
    private final MqUtil mqUtil;

    @SentinelResource(value = "emergency_fence_operation")
    @Transactional(rollbackFor = Exception.class)
    public SecEmergencyFence createFence(EmergencyFenceCreateDTO dto) {
        log.info("创建封控区开始，事件ID：{}，名称：{}，类型：{}", dto.getEventId(), dto.getFenceName(), dto.getFenceType());

        SecEvent event = eventMapper.selectById(dto.getEventId());
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "事件不存在");
        }

        SecEmergencyFence fence = new SecEmergencyFence();
        BeanUtils.copyProperties(dto, fence);
        fence.setId(SnowflakeIdUtil.nextId());
        fence.setStatus(1);

        if (fence.getFenceType() == null) {
            fence.setFenceType(FenceTypeEnum.BLOCKADE_ZONE.getCode());
        }
        applyFenceDefaultStyle(fence);

        if (fence.getCenterLng() == null || fence.getCenterLat() == null) {
            calculateFenceCenter(fence);
        }

        fence.setSortOrder(fence.getSortOrder() != null ? fence.getSortOrder() : 0);
        fenceMapper.insert(fence);

        sendFenceUpdateMq(fence, "CREATE");
        log.info("创建封控区成功，封控区ID：{}", fence.getId());
        return fence;
    }

    @SentinelResource(value = "emergency_fence_operation")
    @Transactional(rollbackFor = Exception.class)
    public void updateFence(Long fenceId, EmergencyFenceCreateDTO dto) {
        log.info("更新封控区开始，封控区ID：{}", fenceId);

        SecEmergencyFence fence = fenceMapper.selectById(fenceId);
        if (fence == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "封控区不存在");
        }

        if (dto.getFenceName() != null) {
            fence.setFenceName(dto.getFenceName());
        }
        if (dto.getFenceType() != null) {
            fence.setFenceType(dto.getFenceType());
            applyFenceDefaultStyle(fence);
        }
        if (dto.getFenceGeometry() != null) {
            fence.setFenceGeometry(dto.getFenceGeometry());
            calculateFenceCenter(fence);
        }
        if (dto.getCenterLng() != null) {
            fence.setCenterLng(dto.getCenterLng());
        }
        if (dto.getCenterLat() != null) {
            fence.setCenterLat(dto.getCenterLat());
        }
        if (dto.getRadiusMeters() != null) {
            fence.setRadiusMeters(dto.getRadiusMeters());
        }
        if (dto.getFillColor() != null) {
            fence.setFillColor(dto.getFillColor());
        }
        if (dto.getStrokeColor() != null) {
            fence.setStrokeColor(dto.getStrokeColor());
        }
        if (dto.getStrokeWeight() != null) {
            fence.setStrokeWeight(dto.getStrokeWeight());
        }
        if (dto.getOpacity() != null) {
            fence.setOpacity(dto.getOpacity());
        }
        if (dto.getSortOrder() != null) {
            fence.setSortOrder(dto.getSortOrder());
        }
        if (dto.getDescription() != null) {
            fence.setDescription(dto.getDescription());
        }

        fenceMapper.updateById(fence);
        sendFenceUpdateMq(fence, "UPDATE");
        log.info("更新封控区成功，封控区ID：{}", fenceId);
    }

    @SentinelResource(value = "emergency_fence_operation")
    @Transactional(rollbackFor = Exception.class)
    public void deleteFence(Long fenceId) {
        log.info("删除封控区开始，封控区ID：{}", fenceId);

        SecEmergencyFence fence = fenceMapper.selectById(fenceId);
        if (fence == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "封控区不存在");
        }

        fence.setStatus(0);
        fenceMapper.updateById(fence);
        sendFenceUpdateMq(fence, "DELETE");
        log.info("删除封控区成功，封控区ID：{}", fenceId);
    }

    public List<SecEmergencyFence> listFences(Long eventId, Integer status) {
        LambdaQueryWrapper<SecEmergencyFence> wrapper = new LambdaQueryWrapper<>();
        if (eventId != null) {
            wrapper.eq(SecEmergencyFence::getEventId, eventId);
        }
        if (status != null) {
            wrapper.eq(SecEmergencyFence::getStatus, status);
        }
        wrapper.orderByAsc(SecEmergencyFence::getSortOrder);
        wrapper.orderByDesc(SecEmergencyFence::getCreateTime);
        return fenceMapper.selectList(wrapper);
    }

    public SecEmergencyFence getFenceById(Long fenceId) {
        SecEmergencyFence fence = fenceMapper.selectById(fenceId);
        if (fence == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "封控区不存在");
        }
        return fence;
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteFences(Long eventId) {
        LambdaQueryWrapper<SecEmergencyFence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecEmergencyFence::getEventId, eventId);
        List<SecEmergencyFence> fences = fenceMapper.selectList(wrapper);
        for (SecEmergencyFence fence : fences) {
            fence.setStatus(0);
            fenceMapper.updateById(fence);
            sendFenceUpdateMq(fence, "DELETE");
        }
        log.info("批量删除事件封控区，事件ID：{}，数量：{}", eventId, fences.size());
        return fences.size();
    }

    private void applyFenceDefaultStyle(SecEmergencyFence fence) {
        FenceTypeEnum typeEnum = FenceTypeEnum.getByCode(fence.getFenceType());
        if (typeEnum == null) {
            typeEnum = FenceTypeEnum.BLOCKADE_ZONE;
        }
        String color = typeEnum.getColor();

        if (fence.getFillColor() == null) {
            fence.setFillColor(color);
        }
        if (fence.getStrokeColor() == null) {
            fence.setStrokeColor(color);
        }
        if (fence.getStrokeWeight() == null) {
            fence.setStrokeWeight(3);
        }
        if (fence.getOpacity() == null) {
            fence.setOpacity(0.35);
        }
    }

    private void calculateFenceCenter(SecEmergencyFence fence) {
        if (fence.getFenceGeometry() == null) {
            return;
        }
        try {
            Polygon polygon = GeoUtil.parsePolygon(fence.getFenceGeometry());
            double[] center = GeoUtil.calculatePolygonCenter(polygon);
            fence.setCenterLng(center[0]);
            fence.setCenterLat(center[1]);
        } catch (Exception e) {
            log.warn("计算封控区中心点失败，封控区ID：{}", fence.getId(), e);
        }
    }

    private void sendFenceUpdateMq(SecEmergencyFence fence, String action) {
        Map<String, Object> message = new HashMap<>();
        message.put("action", action);
        message.put("fenceId", fence.getId());
        message.put("eventId", fence.getEventId());
        message.put("fenceName", fence.getFenceName());
        message.put("fenceType", fence.getFenceType());
        message.put("fenceGeometry", fence.getFenceGeometry());
        message.put("centerLng", fence.getCenterLng());
        message.put("centerLat", fence.getCenterLat());
        message.put("radiusMeters", fence.getRadiusMeters());
        message.put("fillColor", fence.getFillColor());
        message.put("strokeColor", fence.getStrokeColor());
        message.put("strokeWeight", fence.getStrokeWeight());
        message.put("opacity", fence.getOpacity());
        message.put("status", fence.getStatus());
        message.put("timestamp", System.currentTimeMillis());

        mqUtil.sendAsync(
                RocketMQConfig.buildDestination(MqConstant.EMERGENCY_COMMAND_TOPIC, MqConstant.TAG_FENCE_UPDATE),
                message
        );

        mqUtil.sendBroadcast(
                RocketMQConfig.buildDestination(MqConstant.EMERGENCY_COMMAND_TOPIC, MqConstant.TAG_FENCE_UPDATE),
                message
        );

        log.debug("发送封控区MQ消息，封控区ID：{}，操作：{}", fence.getId(), action);
    }
}
