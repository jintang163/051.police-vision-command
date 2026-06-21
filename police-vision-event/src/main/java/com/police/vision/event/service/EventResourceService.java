package com.police.vision.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import com.police.vision.event.entity.SecEvent;
import com.police.vision.event.entity.SecEventResource;
import com.police.vision.event.mapper.SecEventMapper;
import com.police.vision.event.mapper.SecEventResourceMapper;
import com.police.vision.event.util.GeoUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class EventResourceService {

    private final SecEventMapper secEventMapper;
    private final SecEventResourceMapper secEventResourceMapper;

    @Value("${event.resources.default-radius:1000}")
    private Double defaultRadius;

    @Value("${event.resources.mock-police:}")
    private String mockPoliceConfig;

    @Value("${event.resources.mock-camera:}")
    private String mockCameraConfig;

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
        List<MockResource> mockPoliceList = buildMockPoliceList();
        int count = 0;
        for (MockResource resource : mockPoliceList) {
            boolean inPolygon = GeoUtil.isPointInPolygon(resource.getLng(), resource.getLat(), polygon);
            double distance = GeoUtil.haversineDistance(centerLng, centerLat, resource.getLng(), resource.getLat());
            if (inPolygon || distance <= radius) {
                SecEventResource eventResource = new SecEventResource();
                eventResource.setEventId(eventId);
                eventResource.setResourceType(RESOURCE_TYPE_POLICE);
                eventResource.setResourceId(resource.getId());
                eventResource.setResourceName(resource.getName());
                eventResource.setLng(resource.getLng());
                eventResource.setLat(resource.getLat());
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
        List<MockResource> mockCameraList = buildMockCameraList();
        int count = 0;
        for (MockResource resource : mockCameraList) {
            boolean inPolygon = GeoUtil.isPointInPolygon(resource.getLng(), resource.getLat(), polygon);
            double distance = GeoUtil.haversineDistance(centerLng, centerLat, resource.getLng(), resource.getLat());
            if (inPolygon || distance <= radius) {
                SecEventResource eventResource = new SecEventResource();
                eventResource.setEventId(eventId);
                eventResource.setResourceType(RESOURCE_TYPE_CAMERA);
                eventResource.setResourceId(resource.getId());
                eventResource.setResourceName(resource.getName());
                eventResource.setLng(resource.getLng());
                eventResource.setLat(resource.getLat());
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

    private List<MockResource> buildMockPoliceList() {
        List<MockResource> list = new ArrayList<>();
        if (StringUtils.hasText(mockPoliceConfig)) {
            String[] items = mockPoliceConfig.split(";");
            long id = 1000;
            for (String item : items) {
                try {
                    String[] parts = item.split(",");
                    if (parts.length >= 3) {
                        MockResource resource = new MockResource();
                        resource.setId(id++);
                        resource.setName(parts[0]);
                        resource.setLng(Double.parseDouble(parts[1]));
                        resource.setLat(Double.parseDouble(parts[2]));
                        list.add(resource);
                    }
                } catch (Exception e) {
                    log.warn("解析警力配置失败：{}", item, e);
                }
            }
        }
        if (list.isEmpty()) {
            list = getDefaultMockPoliceList();
        }
        return list;
    }

    private List<MockResource> buildMockCameraList() {
        List<MockResource> list = new ArrayList<>();
        if (StringUtils.hasText(mockCameraConfig)) {
            String[] items = mockCameraConfig.split(";");
            long id = 2000;
            for (String item : items) {
                try {
                    String[] parts = item.split(",");
                    if (parts.length >= 3) {
                        MockResource resource = new MockResource();
                        resource.setId(id++);
                        resource.setName(parts[0]);
                        resource.setLng(Double.parseDouble(parts[1]));
                        resource.setLat(Double.parseDouble(parts[2]));
                        list.add(resource);
                    }
                } catch (Exception e) {
                    log.warn("解析摄像头配置失败：{}", item, e);
                }
            }
        }
        if (list.isEmpty()) {
            list = getDefaultMockCameraList();
        }
        return list;
    }

    private List<MockResource> getDefaultMockPoliceList() {
        List<MockResource> list = new ArrayList<>();
        double baseLng = 116.4074;
        double baseLat = 39.9042;
        String[] names = {"张警官", "李警官", "王警官", "赵警官", "刘警官",
                "陈警官", "杨警官", "黄警官", "周警官", "吴警官"};
        long id = 1001;
        for (int i = 0; i < names.length; i++) {
            MockResource resource = new MockResource();
            resource.setId(id++);
            resource.setName(names[i]);
            resource.setLng(baseLng + (i % 5 - 2) * 0.005);
            resource.setLat(baseLat + (i / 5 - 1) * 0.005);
            list.add(resource);
        }
        return list;
    }

    private List<MockResource> getDefaultMockCameraList() {
        List<MockResource> list = new ArrayList<>();
        double baseLng = 116.4074;
        double baseLat = 39.9042;
        String[] names = {"CAM-001", "CAM-002", "CAM-003", "CAM-004", "CAM-005",
                "CAM-006", "CAM-007", "CAM-008", "CAM-009", "CAM-010",
                "CAM-011", "CAM-012"};
        long id = 2001;
        for (int i = 0; i < names.length; i++) {
            MockResource resource = new MockResource();
            resource.setId(id++);
            resource.setName(names[i]);
            resource.setLng(baseLng + (i % 6 - 2.5) * 0.004);
            resource.setLat(baseLat + (i / 6 - 1) * 0.006);
            list.add(resource);
        }
        return list;
    }

    @Data
    private static class MockResource {
        private Long id;
        private String name;
        private Double lng;
        private Double lat;
    }
}
