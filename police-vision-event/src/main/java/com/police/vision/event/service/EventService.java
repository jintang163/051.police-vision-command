package com.police.vision.event.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.event.dto.EventCreateDTO;
import com.police.vision.event.dto.EventUpdateDTO;
import com.police.vision.event.entity.SecEvent;
import com.police.vision.event.entity.SecEventResource;
import com.police.vision.event.mapper.SecEventMapper;
import com.police.vision.event.mapper.SecEventResourceMapper;
import com.police.vision.event.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final SecEventMapper secEventMapper;
    private final SecEventResourceMapper secEventResourceMapper;
    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;

    public static final int EVENT_STATUS_PENDING = 0;
    public static final int EVENT_STATUS_RUNNING = 1;
    public static final int EVENT_STATUS_ENDED = 2;

    @Transactional(rollbackFor = Exception.class)
    public Result<Long> createEvent(EventCreateDTO dto) {
        log.info("创建活动开始，活动名称：{}", dto.getEventName());
        try {
            SecEvent event = new SecEvent();
            BeanUtils.copyProperties(dto, event);
            if (dto.getAreaPolygon() != null && !dto.getAreaPolygon().isEmpty()) {
                String polygonJson = GeoUtil.buildPolygonJson(dto.getAreaPolygon());
                event.setAreaPolygon(polygonJson);
            }
            event.setStatus(EVENT_STATUS_PENDING);
            secEventMapper.insert(event);
            log.info("创建活动成功，活动ID：{}，活动名称：{}", event.getId(), event.getEventName());
            return Result.success(event.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建活动失败", e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "创建活动失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateEvent(EventUpdateDTO dto) {
        log.info("更新活动开始，活动ID：{}", dto.getId());
        SecEvent event = secEventMapper.selectById(dto.getId());
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "活动不存在");
        }
        try {
            if (StringUtils.hasText(dto.getEventName())) {
                event.setEventName(dto.getEventName());
            }
            if (StringUtils.hasText(dto.getEventType())) {
                event.setEventType(dto.getEventType());
            }
            if (StringUtils.hasText(dto.getEventLevel())) {
                event.setEventLevel(dto.getEventLevel());
            }
            if (dto.getStartTime() != null) {
                event.setStartTime(dto.getStartTime());
            }
            if (dto.getEndTime() != null) {
                event.setEndTime(dto.getEndTime());
            }
            if (StringUtils.hasText(dto.getOrganizer())) {
                event.setOrganizer(dto.getOrganizer());
            }
            if (StringUtils.hasText(dto.getDescription())) {
                event.setDescription(dto.getDescription());
            }
            if (dto.getAreaPolygon() != null && !dto.getAreaPolygon().isEmpty()) {
                String polygonJson = GeoUtil.buildPolygonJson(dto.getAreaPolygon());
                event.setAreaPolygon(polygonJson);
            }
            secEventMapper.updateById(event);
            log.info("更新活动成功，活动ID：{}", dto.getId());
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新活动失败，活动ID：{}", dto.getId(), e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "更新活动失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteEvent(Long id) {
        log.info("删除活动开始，活动ID：{}", id);
        SecEvent event = secEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "活动不存在");
        }
        if (EVENT_STATUS_RUNNING == event.getStatus()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动进行中，无法删除，请先结束活动");
        }
        try {
            secEventMapper.deleteById(id);
            LambdaQueryWrapper<SecEventResource> resourceWrapper = new LambdaQueryWrapper<>();
            resourceWrapper.eq(SecEventResource::getEventId, id);
            secEventResourceMapper.delete(resourceWrapper);
            String cacheKey = "event:detail:" + id;
            redisUtil.delete(cacheKey);
            log.info("删除活动成功，活动ID：{}", id);
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除活动失败，活动ID：{}", id, e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "删除活动失败：" + e.getMessage());
        }
    }

    public Result<SecEvent> getEventById(Long id) {
        log.debug("查询活动详情，活动ID：{}", id);
        String cacheKey = "event:detail:" + id;
        SecEvent cachedEvent = redisUtil.getObject(cacheKey, SecEvent.class);
        if (cachedEvent != null) {
            return Result.success(cachedEvent);
        }
        SecEvent event = secEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "活动不存在");
        }
        redisUtil.setObject(cacheKey, event, 30, java.util.concurrent.TimeUnit.MINUTES);
        return Result.success(event);
    }

    public Result<PageResult<SecEvent>> listEvents(String keyword, Integer status, int page, int size) {
        log.debug("分页查询活动列表，keyword：{}，status：{}，page：{}，size：{}", keyword, status, page, size);
        LambdaQueryWrapper<SecEvent> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SecEvent::getEventName, keyword)
                    .or()
                    .like(SecEvent::getDescription, keyword);
        }
        if (status != null) {
            wrapper.eq(SecEvent::getStatus, status);
        }
        wrapper.orderByDesc(SecEvent::getCreateTime);
        Page<SecEvent> pageParam = new Page<>(page, size);
        IPage<SecEvent> result = secEventMapper.selectPage(pageParam, wrapper);
        PageResult<SecEvent> pageResult = PageResult.of(result.getTotal(), result.getRecords(), page, size);
        return Result.success(pageResult);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> startEvent(Long id) {
        log.info("启动活动开始，活动ID：{}", id);
        SecEvent event = secEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "活动不存在");
        }
        if (EVENT_STATUS_RUNNING == event.getStatus()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动已在进行中");
        }
        if (EVENT_STATUS_ENDED == event.getStatus()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动已结束，无法重新启动");
        }
        try {
            event.setStatus(EVENT_STATUS_RUNNING);
            secEventMapper.updateById(event);
            Map<String, Object> message = new HashMap<>();
            message.put("eventId", event.getId());
            message.put("eventName", event.getEventName());
            message.put("action", "start");
            message.put("timestamp", System.currentTimeMillis());
            mqUtil.send(MqConstant.DISPATCH_TOPIC + ":" + MqConstant.TAG_NOTIFY, message);
            String cacheKey = "event:detail:" + id;
            redisUtil.delete(cacheKey);
            log.info("启动活动成功，活动ID：{}，活动名称：{}", id, event.getEventName());
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("启动活动失败，活动ID：{}", id, e);
            throw new BusinessException(ResultCode.MQ_ERROR, "启动活动失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> endEvent(Long id) {
        log.info("结束活动开始，活动ID：{}", id);
        SecEvent event = secEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "活动不存在");
        }
        if (EVENT_STATUS_PENDING == event.getStatus()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动未启动，无需结束");
        }
        if (EVENT_STATUS_ENDED == event.getStatus()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动已结束");
        }
        try {
            event.setStatus(EVENT_STATUS_ENDED);
            secEventMapper.updateById(event);
            Map<String, Object> message = new HashMap<>();
            message.put("eventId", event.getId());
            message.put("eventName", event.getEventName());
            message.put("action", "end");
            message.put("timestamp", System.currentTimeMillis());
            mqUtil.send(MqConstant.DISPATCH_TOPIC + ":" + MqConstant.TAG_NOTIFY, message);
            String cacheKey = "event:detail:" + id;
            redisUtil.delete(cacheKey);
            log.info("结束活动成功，活动ID：{}，活动名称：{}", id, event.getEventName());
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("结束活动失败，活动ID：{}", id, e);
            throw new BusinessException(ResultCode.MQ_ERROR, "结束活动失败：" + e.getMessage());
        }
    }
}
