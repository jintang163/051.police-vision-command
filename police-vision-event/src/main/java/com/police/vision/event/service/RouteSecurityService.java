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
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.event.dto.RouteCameraDTO;
import com.police.vision.event.dto.RouteCreateDTO;
import com.police.vision.event.dto.RoutePatrolDTO;
import com.police.vision.event.entity.SecEventResource;
import com.police.vision.event.entity.SecRoute;
import com.police.vision.event.entity.SecRouteCamera;
import com.police.vision.event.mapper.SecEventResourceMapper;
import com.police.vision.event.mapper.SecRouteCameraMapper;
import com.police.vision.event.mapper.SecRouteMapper;
import com.police.vision.event.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteSecurityService {

    private final SecRouteMapper secRouteMapper;
    private final SecRouteCameraMapper secRouteCameraMapper;
    private final SecEventResourceMapper secEventResourceMapper;
    private final MqUtil mqUtil;
    private final RedisUtil redisUtil;

    private static final double INTERPOLATION_INTERVAL = 50;
    private static final double CAMERA_SEARCH_RADIUS = 200;
    private static final int DEFAULT_PLAY_DURATION = 10;
    private static final String ROUTE_PATROL_STATUS_PREFIX = "route:patrol:status:";
    private static final long ROUTE_PATROL_EXPIRE_HOURS = 24;

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

        List<Long> cameraIds = dto.getCameraIds();
        if (cameraIds != null && !cameraIds.isEmpty()) {
            saveRouteCameras(route.getId(), cameraIds, dto.getEventId());
        } else if (dto.getWaypoints() != null && !dto.getWaypoints().isEmpty()) {
            List<Long> autoSelectedCameraIds = autoSelectCameras(dto.getWaypoints(), dto.getEventId());
            if (!autoSelectedCameraIds.isEmpty()) {
                saveRouteCameras(route.getId(), autoSelectedCameraIds, dto.getEventId());
            }
        }

        return route;
    }

    private List<Long> autoSelectCameras(List<Map<String, Double>> waypoints, Long eventId) {
        List<double[]> pointList = new ArrayList<>();
        for (Map<String, Double> point : waypoints) {
            Double lng = point.get("lng");
            Double lat = point.get("lat");
            if (lng == null || lat == null) {
                lng = point.get("longitude");
                lat = point.get("latitude");
            }
            if (lng != null && lat != null) {
                pointList.add(new double[]{lng, lat});
            }
        }

        if (pointList.size() < 2) {
            return new ArrayList<>();
        }

        List<double[]> interpolatedPoints = GeoUtil.interpolatePoints(pointList, INTERPOLATION_INTERVAL);

        LambdaQueryWrapper<SecEventResource> resourceWrapper = new LambdaQueryWrapper<>();
        resourceWrapper.eq(SecEventResource::getResourceType, "CAMERA");
        resourceWrapper.isNotNull(SecEventResource::getLng);
        resourceWrapper.isNotNull(SecEventResource::getLat);
        if (eventId != null) {
            resourceWrapper.eq(SecEventResource::getEventId, eventId);
        }
        List<SecEventResource> allCameras = secEventResourceMapper.selectList(resourceWrapper);

        Set<Long> selectedCameraIds = new HashSet<>();
        Map<Long, SecEventResource> cameraMap = new HashMap<>();
        Map<Long, Double> cameraMinDistanceMap = new HashMap<>();
        Map<Long, Integer> cameraFirstIndexMap = new HashMap<>();

        for (int i = 0; i < interpolatedPoints.size(); i++) {
            double[] point = interpolatedPoints.get(i);
            for (SecEventResource camera : allCameras) {
                if (camera.getLng() == null || camera.getLat() == null) {
                    continue;
                }
                double distance = GeoUtil.pointToPolylineDistance(camera.getLng(), camera.getLat(), pointList);
                if (distance <= CAMERA_SEARCH_RADIUS) {
                    Long cameraId = camera.getResourceId();
                    selectedCameraIds.add(cameraId);
                    cameraMap.put(cameraId, camera);
                    if (!cameraMinDistanceMap.containsKey(cameraId) || distance < cameraMinDistanceMap.get(cameraId)) {
                        cameraMinDistanceMap.put(cameraId, distance);
                    }
                    if (!cameraFirstIndexMap.containsKey(cameraId)) {
                        cameraFirstIndexMap.put(cameraId, i);
                    }
                }
            }
        }

        List<Long> sortedCameraIds = selectedCameraIds.stream()
                .sorted(Comparator.comparingInt(cameraFirstIndexMap::get))
                .collect(Collectors.toList());

        log.info("自动选点完成，共选择 {} 个摄像头", sortedCameraIds.size());
        return sortedCameraIds;
    }

    private void saveRouteCameras(Long routeId, List<Long> cameraIds, Long eventId) {
        LambdaQueryWrapper<SecEventResource> resourceWrapper = new LambdaQueryWrapper<>();
        resourceWrapper.in(SecEventResource::getResourceId, cameraIds);
        resourceWrapper.eq(SecEventResource::getResourceType, "CAMERA");
        List<SecEventResource> resources = secEventResourceMapper.selectList(resourceWrapper);
        Map<Long, SecEventResource> resourceMap = resources.stream()
                .collect(Collectors.toMap(SecEventResource::getResourceId, r -> r));

        int cameraIndex = 1;
        for (Long cameraId : cameraIds) {
            SecEventResource resource = resourceMap.get(cameraId);
            if (resource != null) {
                SecRouteCamera routeCamera = new SecRouteCamera();
                routeCamera.setId(SnowflakeIdUtil.nextId());
                routeCamera.setRouteId(routeId);
                routeCamera.setCameraId(cameraId);
                routeCamera.setCameraName(resource.getResourceName());
                routeCamera.setCameraIndex(cameraIndex++);
                routeCamera.setPlayDuration(DEFAULT_PLAY_DURATION);
                secRouteCameraMapper.insert(routeCamera);
            }
        }
        log.info("创建路线摄像头关联成功，routeId: {}, 摄像头数量: {}", routeId, cameraIds.size());
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

    public RoutePatrolDTO startRoutePatrol(Long routeId) {
        SecRoute route = getRouteById(routeId);

        LambdaQueryWrapper<SecRouteCamera> cameraWrapper = new LambdaQueryWrapper<>();
        cameraWrapper.eq(SecRouteCamera::getRouteId, routeId);
        cameraWrapper.orderByAsc(SecRouteCamera::getCameraIndex);
        List<SecRouteCamera> cameras = secRouteCameraMapper.selectList(cameraWrapper);

        if (cameras.isEmpty()) {
            throw new BusinessException("路线没有关联摄像头，无法启动轮巡");
        }

        String patrolTaskId = SnowflakeIdUtil.nextIdStr();

        List<RouteCameraDTO> cameraList = new ArrayList<>();
        for (SecRouteCamera camera : cameras) {
            RouteCameraDTO cameraDTO = new RouteCameraDTO();
            cameraDTO.setCameraId(camera.getCameraId());
            cameraDTO.setCameraName(camera.getCameraName());
            cameraDTO.setCameraUrl(camera.getCameraUrl());
            cameraDTO.setCameraIndex(camera.getCameraIndex());
            cameraDTO.setPlayDuration(camera.getPlayDuration() != null ? camera.getPlayDuration() : DEFAULT_PLAY_DURATION);
            cameraList.add(cameraDTO);
        }

        RoutePatrolDTO patrolDTO = new RoutePatrolDTO();
        patrolDTO.setRouteId(routeId);
        patrolDTO.setEventId(route.getEventId());
        patrolDTO.setPatrolTaskId(patrolTaskId);
        patrolDTO.setCameras(cameraList);
        patrolDTO.setStatus("RUNNING");
        patrolDTO.setStartTime(System.currentTimeMillis());

        Map<String, Object> patrolCommand = new HashMap<>();
        patrolCommand.put("routeId", routeId);
        patrolCommand.put("patrolTaskId", patrolTaskId);
        patrolCommand.put("cameras", cameraList);

        Map<String, Object> patrolMessage = new HashMap<>();
        patrolMessage.put("type", "route_patrol_start");
        patrolMessage.put("data", patrolCommand);
        mqUtil.sendAsync(RocketMQConfig.buildDestination(MqConstant.VIDEO_ANALYSIS_TOPIC, "route_patrol"), patrolMessage);
        log.info("启动路线轮巡任务，routeId: {}, patrolTaskId: {}, 摄像头数量: {}", routeId, patrolTaskId, cameras.size());

        String redisKey = ROUTE_PATROL_STATUS_PREFIX + routeId;
        redisUtil.setObject(redisKey, patrolDTO, ROUTE_PATROL_EXPIRE_HOURS, TimeUnit.HOURS);

        return patrolDTO;
    }

    public RoutePatrolDTO pauseRoutePatrol(Long routeId) {
        String redisKey = ROUTE_PATROL_STATUS_PREFIX + routeId;
        RoutePatrolDTO patrolDTO = redisUtil.getObject(redisKey, RoutePatrolDTO.class);
        if (patrolDTO == null) {
            throw new BusinessException("路线轮巡任务不存在或已结束");
        }
        if ("PAUSED".equals(patrolDTO.getStatus())) {
            throw new BusinessException("路线轮巡已暂停");
        }

        patrolDTO.setStatus("PAUSED");
        redisUtil.setObject(redisKey, patrolDTO, ROUTE_PATROL_EXPIRE_HOURS, TimeUnit.HOURS);

        Map<String, Object> pauseMessage = new HashMap<>();
        pauseMessage.put("type", "route_patrol_pause");
        pauseMessage.put("data", Map.of("routeId", routeId, "patrolTaskId", patrolDTO.getPatrolTaskId()));
        mqUtil.sendAsync(RocketMQConfig.buildDestination(MqConstant.VIDEO_ANALYSIS_TOPIC, "route_patrol"), pauseMessage);
        log.info("暂停路线轮巡，routeId: {}, patrolTaskId: {}", routeId, patrolDTO.getPatrolTaskId());

        return patrolDTO;
    }

    public RoutePatrolDTO resumeRoutePatrol(Long routeId) {
        String redisKey = ROUTE_PATROL_STATUS_PREFIX + routeId;
        RoutePatrolDTO patrolDTO = redisUtil.getObject(redisKey, RoutePatrolDTO.class);
        if (patrolDTO == null) {
            throw new BusinessException("路线轮巡任务不存在或已结束");
        }
        if ("RUNNING".equals(patrolDTO.getStatus())) {
            throw new BusinessException("路线轮巡正在运行中");
        }

        patrolDTO.setStatus("RUNNING");
        redisUtil.setObject(redisKey, patrolDTO, ROUTE_PATROL_EXPIRE_HOURS, TimeUnit.HOURS);

        Map<String, Object> resumeMessage = new HashMap<>();
        resumeMessage.put("type", "route_patrol_resume");
        resumeMessage.put("data", Map.of("routeId", routeId, "patrolTaskId", patrolDTO.getPatrolTaskId()));
        mqUtil.sendAsync(RocketMQConfig.buildDestination(MqConstant.VIDEO_ANALYSIS_TOPIC, "route_patrol"), resumeMessage);
        log.info("恢复路线轮巡，routeId: {}, patrolTaskId: {}", routeId, patrolDTO.getPatrolTaskId());

        return patrolDTO;
    }

    public RoutePatrolDTO stopRoutePatrol(Long routeId) {
        String redisKey = ROUTE_PATROL_STATUS_PREFIX + routeId;
        RoutePatrolDTO patrolDTO = redisUtil.getObject(redisKey, RoutePatrolDTO.class);
        if (patrolDTO == null) {
            throw new BusinessException("路线轮巡任务不存在或已结束");
        }

        patrolDTO.setStatus("STOPPED");
        redisUtil.delete(redisKey);

        Map<String, Object> stopMessage = new HashMap<>();
        stopMessage.put("type", "route_patrol_stop");
        stopMessage.put("data", Map.of("routeId", routeId, "patrolTaskId", patrolDTO.getPatrolTaskId()));
        mqUtil.sendAsync(RocketMQConfig.buildDestination(MqConstant.VIDEO_ANALYSIS_TOPIC, "route_patrol"), stopMessage);
        log.info("停止路线轮巡，routeId: {}, patrolTaskId: {}", routeId, patrolDTO.getPatrolTaskId());

        return patrolDTO;
    }

    public RoutePatrolDTO getRoutePatrolStatus(Long routeId) {
        String redisKey = ROUTE_PATROL_STATUS_PREFIX + routeId;
        RoutePatrolDTO patrolDTO = redisUtil.getObject(redisKey, RoutePatrolDTO.class);
        if (patrolDTO == null) {
            SecRoute route = getRouteById(routeId);
            patrolDTO = new RoutePatrolDTO();
            patrolDTO.setRouteId(routeId);
            patrolDTO.setEventId(route.getEventId());
            patrolDTO.setStatus("IDLE");
        }
        return patrolDTO;
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
