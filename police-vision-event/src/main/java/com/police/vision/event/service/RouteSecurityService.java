package com.police.vision.event.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.event.dto.RouteCreateDTO;
import com.police.vision.event.entity.SecEventResource;
import com.police.vision.event.entity.SecRoute;
import com.police.vision.event.entity.SecRouteCamera;
import com.police.vision.event.mapper.SecEventResourceMapper;
import com.police.vision.event.mapper.SecRouteCameraMapper;
import com.police.vision.event.mapper.SecRouteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteSecurityService {

    private final SecRouteMapper secRouteMapper;
    private final SecRouteCameraMapper secRouteCameraMapper;
    private final SecEventResourceMapper secEventResourceMapper;
    private final MqUtil mqUtil;

    @Transactional(rollbackFor = Exception.class)
    public SecRoute createRoute(RouteCreateDTO dto) {
        SecRoute route = new SecRoute();
        BeanUtils.copyProperties(dto, route);
        route.setId(SnowflakeIdUtil.nextId());
        if (dto.getWaypoints() != null) {
            route.setWaypoints(JSON.toJSONString(dto.getWaypoints()));
        }
        route.setStatus(0);
        secRouteMapper.insert(route);
        log.info("创建路线成功，routeId: {}", route.getId());

        if (dto.getCameraIds() != null && !dto.getCameraIds().isEmpty()) {
            LambdaQueryWrapper<SecEventResource> resourceWrapper = new LambdaQueryWrapper<>();
            resourceWrapper.in(SecEventResource::getResourceId, dto.getCameraIds());
            resourceWrapper.eq(SecEventResource::getResourceType, "CAMERA");
            List<SecEventResource> resources = secEventResourceMapper.selectList(resourceWrapper);
            Map<Long, SecEventResource> resourceMap = resources.stream()
                    .collect(Collectors.toMap(SecEventResource::getResourceId, r -> r));

            int cameraIndex = 1;
            for (Long cameraId : dto.getCameraIds()) {
                SecEventResource resource = resourceMap.get(cameraId);
                if (resource != null) {
                    SecRouteCamera routeCamera = new SecRouteCamera();
                    routeCamera.setId(SnowflakeIdUtil.nextId());
                    routeCamera.setRouteId(route.getId());
                    routeCamera.setCameraId(cameraId);
                    routeCamera.setCameraName(resource.getResourceName());
                    routeCamera.setCameraIndex(cameraIndex++);
                    routeCamera.setPlayDuration(10);
                    secRouteCameraMapper.insert(routeCamera);
                }
            }
            log.info("创建路线摄像头关联成功，routeId: {}, 摄像头数量: {}", route.getId(), dto.getCameraIds().size());
        }
        return route;
    }

    public Map<String, Object> getRouteDetail(Long routeId) {
        SecRoute route = getRouteById(routeId);
        Map<String, Object> result = new HashMap<>();
        result.put("id", route.getId());
        result.put("eventId", route.getEventId());
        result.put("routeName", route.getRouteName());
        result.put("startPoint", route.getStartPoint());
        result.put("endPoint", route.getEndPoint());
        result.put("waypoints", route.getWaypoints() != null ? JSON.parse(route.getWaypoints()) : null);
        result.put("status", route.getStatus());
        result.put("createTime", route.getCreateTime());
        result.put("updateTime", route.getUpdateTime());

        LambdaQueryWrapper<SecRouteCamera> cameraWrapper = new LambdaQueryWrapper<>();
        cameraWrapper.eq(SecRouteCamera::getRouteId, routeId);
        cameraWrapper.orderByAsc(SecRouteCamera::getCameraIndex);
        List<SecRouteCamera> cameras = secRouteCameraMapper.selectList(cameraWrapper);
        result.put("cameras", cameras);
        return result;
    }

    public PageResult<SecRoute> listRoutes(Long eventId, int page, int size) {
        LambdaQueryWrapper<SecRoute> wrapper = new LambdaQueryWrapper<>();
        if (eventId != null) {
            wrapper.eq(SecRoute::getEventId, eventId);
        }
        wrapper.orderByDesc(SecRoute::getCreateTime);

        Page<SecRoute> pageParam = new Page<>(page, size);
        IPage<SecRoute> result = secRouteMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    public Map<String, Object> startRoutePatrol(Long routeId) {
        SecRoute route = getRouteById(routeId);

        LambdaQueryWrapper<SecRouteCamera> cameraWrapper = new LambdaQueryWrapper<>();
        cameraWrapper.eq(SecRouteCamera::getRouteId, routeId);
        cameraWrapper.orderByAsc(SecRouteCamera::getCameraIndex);
        List<SecRouteCamera> cameras = secRouteCameraMapper.selectList(cameraWrapper);

        List<Map<String, Object>> cameraList = new ArrayList<>();
        Integer playInterval = 10;
        for (SecRouteCamera camera : cameras) {
            Map<String, Object> cameraMap = new HashMap<>();
            cameraMap.put("cameraId", camera.getCameraId());
            cameraMap.put("cameraName", camera.getCameraName());
            cameraMap.put("cameraUrl", camera.getCameraUrl());
            cameraMap.put("cameraIndex", camera.getCameraIndex());
            cameraMap.put("playDuration", camera.getPlayDuration());
            if (camera.getPlayDuration() != null) {
                playInterval = camera.getPlayDuration();
            }
            cameraList.add(cameraMap);
        }

        Map<String, Object> patrolCommand = new HashMap<>();
        patrolCommand.put("routeId", routeId);
        patrolCommand.put("routeName", route.getRouteName());
        patrolCommand.put("cameras", cameraList);
        patrolCommand.put("playInterval", playInterval);
        patrolCommand.put("startTime", System.currentTimeMillis());

        Map<String, Object> patrolMessage = new HashMap<>();
        patrolMessage.put("type", "route_patrol_start");
        patrolMessage.put("data", patrolCommand);
        mqUtil.sendAsync(RocketMQConfig.buildDestination(MqConstant.VIDEO_ANALYSIS_TOPIC, "route_patrol"), patrolMessage);
        log.info("启动路线轮巡任务，routeId: {}, 摄像头数量: {}", routeId, cameras.size());

        return patrolCommand;
    }

    public List<String> getPatrolCameras(Long routeId) {
        LambdaQueryWrapper<SecRouteCamera> cameraWrapper = new LambdaQueryWrapper<>();
        cameraWrapper.eq(SecRouteCamera::getRouteId, routeId);
        cameraWrapper.orderByAsc(SecRouteCamera::getCameraIndex);
        List<SecRouteCamera> cameras = secRouteCameraMapper.selectList(cameraWrapper);

        return cameras.stream()
                .map(SecRouteCamera::getCameraUrl)
                .filter(url -> url != null && !url.isEmpty())
                .collect(Collectors.toList());
    }

    private SecRoute getRouteById(Long routeId) {
        SecRoute route = secRouteMapper.selectById(routeId);
        if (route == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "路线不存在");
        }
        return route;
    }
}
