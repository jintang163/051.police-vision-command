package com.police.vision.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import com.police.vision.event.entity.SecEvent;
import com.police.vision.event.entity.SecEventResource;
import com.police.vision.event.entity.vo.CameraPointVO;
import com.police.vision.event.entity.vo.PoliceLocationVO;
import com.police.vision.event.feign.GisFeignClient;
import com.police.vision.event.mapper.SecEventMapper;
import com.police.vision.event.mapper.SecEventResourceMapper;
import com.police.vision.event.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class EventResourceService {

    private final SecEventMapper secEventMapper;
    private final SecEventResourceMapper secEventResourceMapper;
    private final GisFeignClient gisFeignClient;

    @Value("${event.resources.default-radius:1000}")
    private Double defaultRadius;

    public static final String RESOURCE_TYPE_POLICE = "police";
    public static final String RESOURCE_TYPE_CAMERA = "camera";

    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> allocateResources(Long eventId, Double radius) {
        log.info("分配活动资源开始，活动ID：{}，搜索半径：{}米", eventId, radius);
        SecEvent event = secEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "活动不存在");
        }
        if (!StringUtils.hasText(event.getAreaPolygon())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动区域多边形未设置，无法分配资源");
        }
        double searchRadius = (radius != null && radius > 0) ? radius : defaultRadius;
        try {
            Polygon polygon = GeoUtil.parsePolygon(event.getAreaPolygon());
            double[] center = GeoUtil.calculatePolygonCenter(polygon);
            double centerLng = center[0];
            double centerLat = center[1];
            log.info("活动区域中心点：经度={}, 纬度={}", centerLng, centerLat);

            clearEventResources(eventId);

            int policeCount = allocatePoliceResources(eventId, polygon, centerLng, centerLat, searchRadius);
            int cameraCount = allocateCameraResources(eventId, polygon, centerLng, centerLat, searchRadius);

            int total = policeCount + cameraCount;
            log.info("分配活动资源完成，活动ID：{}，警力：{}人，摄像头：{}个，总计：{}个",
                    eventId, policeCount, cameraCount, total);
            return Result.success(total);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("分配活动资源失败，活动ID：{}", eventId, e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "分配活动资源失败：" + e.getMessage());
        }
    }

    public Result<List<SecEventResource>> listPoliceResources(Long eventId) {
        log.debug("查询活动警力资源列表，活动ID：{}", eventId);
        if (eventId == null) {
            throw new BusinessException(ResultCode.PARAM_NULL, "活动ID不能为空");
        }
        LambdaQueryWrapper<SecEventResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecEventResource::getEventId, eventId)
                .eq(SecEventResource::getResourceType, RESOURCE_TYPE_POLICE)
                .orderByAsc(SecEventResource::getDistance);
        List<SecEventResource> list = secEventResourceMapper.selectList(wrapper);
        return Result.success(list);
    }

    public Result<List<SecEventResource>> listCameraResources(Long eventId) {
        log.debug("查询活动摄像头资源列表，活动ID：{}", eventId);
        if (eventId == null) {
            throw new BusinessException(ResultCode.PARAM_NULL, "活动ID不能为空");
        }
        LambdaQueryWrapper<SecEventResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecEventResource::getEventId, eventId)
                .eq(SecEventResource::getResourceType, RESOURCE_TYPE_CAMERA)
                .orderByAsc(SecEventResource::getDistance);
        List<SecEventResource> list = secEventResourceMapper.selectList(wrapper);
        return Result.success(list);
    }

    private int allocatePoliceResources(Long eventId, Polygon polygon,
                                        double centerLng, double centerLat, double radius) {
        Result<List<PoliceLocationVO>> result = gisFeignClient.getPoliceDistribution();
        if (result == null || !result.isSuccess()) {
            String errorMsg = (result != null) ? result.getMessage() : "调用GIS服务失败";
            log.error("调用GIS服务获取警力分布失败：{}", errorMsg);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "获取警力分布失败：" + errorMsg);
        }
        List<PoliceLocationVO> policeList = result.getData();
        if (policeList == null || policeList.isEmpty()) {
            log.warn("GIS服务返回空警力列表");
            return 0;
        }

        int count = 0;
        for (PoliceLocationVO police : policeList) {
            if (police.getLongitude() == null || police.getLatitude() == null) {
                continue;
            }
            double lng = police.getLongitude().doubleValue();
            double lat = police.getLatitude().doubleValue();
            boolean inPolygon = GeoUtil.isPointInPolygon(lng, lat, polygon);
            double distance = GeoUtil.haversineDistance(centerLng, centerLat, lng, lat);
            if (inPolygon || distance <= radius) {
                SecEventResource eventResource = new SecEventResource();
                eventResource.setEventId(eventId);
                eventResource.setResourceType(RESOURCE_TYPE_POLICE);
                eventResource.setResourceId(police.getPoliceId());
                eventResource.setResourceName(police.getName());
                eventResource.setLng(lng);
                eventResource.setLat(lat);
                eventResource.setDistance(distance);
                secEventResourceMapper.insert(eventResource);
                count++;
            }
        }
        log.info("分配警力资源完成，活动ID：{}，数量：{}", eventId, count);
        return count;
    }

    private int allocateCameraResources(Long eventId, Polygon polygon,
                                        double centerLng, double centerLat, double radius) {
        Result<List<CameraPointVO>> result = gisFeignClient.getCameraPoints();
        if (result == null || !result.isSuccess()) {
            String errorMsg = (result != null) ? result.getMessage() : "调用GIS服务失败";
            log.error("调用GIS服务获取摄像头点位失败：{}", errorMsg);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "获取摄像头点位失败：" + errorMsg);
        }
        List<CameraPointVO> cameraList = result.getData();
        if (cameraList == null || cameraList.isEmpty()) {
            log.warn("GIS服务返回空摄像头列表");
            return 0;
        }

        int count = 0;
        for (CameraPointVO camera : cameraList) {
            if (camera.getLongitude() == null || camera.getLatitude() == null) {
                continue;
            }
            double lng = camera.getLongitude().doubleValue();
            double lat = camera.getLatitude().doubleValue();
            boolean inPolygon = GeoUtil.isPointInPolygon(lng, lat, polygon);
            double distance = GeoUtil.haversineDistance(centerLng, centerLat, lng, lat);
            if (inPolygon || distance <= radius) {
                SecEventResource eventResource = new SecEventResource();
                eventResource.setEventId(eventId);
                eventResource.setResourceType(RESOURCE_TYPE_CAMERA);
                eventResource.setResourceId(camera.getId());
                eventResource.setResourceName(camera.getName());
                eventResource.setLng(lng);
                eventResource.setLat(lat);
                eventResource.setDistance(distance);
                secEventResourceMapper.insert(eventResource);
                count++;
            }
        }
        log.info("分配摄像头资源完成，活动ID：{}，数量：{}", eventId, count);
        return count;
    }

    private void clearEventResources(Long eventId) {
        LambdaQueryWrapper<SecEventResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecEventResource::getEventId, eventId);
        int deleted = secEventResourceMapper.delete(wrapper);
        log.info("清除活动旧资源，活动ID：{}，删除数量：{}", eventId, deleted);
    }
}
