package com.police.vision.traffic.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson2.JSON;
import com.police.vision.common.entity.TrafficCaptureData;
import com.police.vision.common.entity.VehicleTrackPoint;
import com.police.vision.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleTrackService {

    private final ElasticsearchClient elasticsearchClient;

    private static final String TRACK_INDEX = "vehicle_track";

    @SentinelResource(value = "vehicle_track_store", blockHandler = "handleStoreBlock")
    public void storeTrackPoint(TrafficCaptureData captureData) {
        try {
            VehicleTrackPoint trackPoint = convertToTrackPoint(captureData);
            IndexRequest<VehicleTrackPoint> request = IndexRequest.of(i -> i
                    .index(TRACK_INDEX)
                    .id(trackPoint.getTrackId())
                    .document(trackPoint)
            );
            elasticsearchClient.index(request);
            log.debug("车辆轨迹点已存储：plateNo={}, crossing={}", trackPoint.getPlateNo(), trackPoint.getCrossingName());
        } catch (Exception e) {
            log.error("存储车辆轨迹点失败：", e);
        }
    }

    public void handleStoreBlock(TrafficCaptureData captureData, BlockException ex) {
        log.warn("车辆轨迹存储触发Sentinel限流：plateNo={}, rule={}",
                captureData.getPlateNo(), ex.getRule().getResource());
    }

    private VehicleTrackPoint convertToTrackPoint(TrafficCaptureData data) {
        VehicleTrackPoint point = new VehicleTrackPoint();
        point.setTrackId("TP" + SnowflakeIdUtil.nextId());
        point.setPlateNo(data.getPlateNo());
        point.setVehicleType(data.getVehicleType());
        point.setVehicleColor(data.getVehicleColor());
        point.setCrossingId(data.getCrossingId());
        point.setCrossingName(data.getCrossingName());
        point.setCameraId(data.getCameraId());
        point.setCameraName(data.getCameraName());
        point.setLongitude(data.getLongitude());
        point.setLatitude(data.getLatitude());
        point.setSpeed(data.getSpeed());
        point.setDirection(data.getDirection());
        point.setLaneNo(data.getLaneNo());
        point.setImageUrl(data.getImageUrl());
        point.setCaptureTime(data.getCaptureTime());
        if (data.getCaptureTime() != null) {
            point.setTimestamp(data.getCaptureTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } else {
            point.setTimestamp(System.currentTimeMillis());
        }
        return point;
    }

    public List<VehicleTrackPoint> getVehicleTrack(String plateNo, LocalDateTime startTime, LocalDateTime endTime) {
        List<VehicleTrackPoint> trackPoints = new ArrayList<>();
        try {
            long startMs = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endMs = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            SearchRequest request = SearchRequest.of(s -> s
                    .index(TRACK_INDEX)
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m
                                            .term(t -> t
                                                    .field("plateNo")
                                                    .value(plateNo)
                                            )
                                    )
                                    .filter(f -> f
                                            .range(r -> r
                                                    .field("timestamp")
                                                    .gte(String.valueOf(startMs))
                                                    .lte(String.valueOf(endMs))
                                            )
                                    )
                            )
                    )
                    .sort(so -> so
                            .field(f -> f
                                    .field("timestamp")
                                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)
                            )
                    )
                    .size(1000)
            );

            SearchResponse<VehicleTrackPoint> response = elasticsearchClient.search(request, VehicleTrackPoint.class);
            for (Hit<VehicleTrackPoint> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    trackPoints.add(hit.source());
                }
            }
            log.info("查询车辆轨迹：plateNo={}, 轨迹点数={}", plateNo, trackPoints.size());
        } catch (Exception e) {
            log.error("查询车辆轨迹失败：", e);
        }
        return trackPoints;
    }

    public List<String> searchVehicles(String keyword, int limit) {
        List<String> vehicles = new ArrayList<>();
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(TRACK_INDEX)
                    .query(q -> q
                            .wildcard(w -> w
                                    .field("plateNo")
                                    .value("*" + keyword + "*")
                            )
                    )
                    .collapse(c -> c
                            .field("plateNo")
                    )
                    .size(limit)
            );

            SearchResponse<VehicleTrackPoint> response = elasticsearchClient.search(request, VehicleTrackPoint.class);
            for (Hit<VehicleTrackPoint> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    vehicles.add(hit.source().getPlateNo());
                }
            }
        } catch (Exception e) {
            log.error("搜索车辆失败：", e);
        }
        return vehicles;
    }

    public void createTrackIndex() {
        try {
            boolean exists = elasticsearchClient.indices().exists(e -> e.index(TRACK_INDEX)).value();
            if (!exists) {
                elasticsearchClient.indices().create(c -> c
                        .index(TRACK_INDEX)
                        .mappings(m -> m
                                .properties("plateNo", p -> p.keyword(k -> k))
                                .properties("vehicleType", p -> p.keyword(k -> k))
                                .properties("vehicleColor", p -> p.keyword(k -> k))
                                .properties("crossingId", p -> p.keyword(k -> k))
                                .properties("crossingName", p -> p.keyword(k -> k))
                                .properties("cameraId", p -> p.keyword(k -> k))
                                .properties("cameraName", p -> p.keyword(k -> k))
                                .properties("longitude", p -> p.double_(d -> d))
                                .properties("latitude", p -> p.double_(d -> d))
                                .properties("speed", p -> p.double_(d -> d))
                                .properties("direction", p -> p.integer(i -> i))
                                .properties("laneNo", p -> p.keyword(k -> k))
                                .properties("imageUrl", p -> p.keyword(k -> k))
                                .properties("captureTime", p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss")))
                                .properties("timestamp", p -> p.long_(l -> l))
                        )
                );
                log.info("车辆轨迹索引创建成功：{}", TRACK_INDEX);
            }
        } catch (Exception e) {
            log.error("创建车辆轨迹索引失败：", e);
        }
    }
}
