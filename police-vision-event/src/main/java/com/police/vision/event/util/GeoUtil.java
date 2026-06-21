package com.police.vision.event.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class GeoUtil {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final double EARTH_RADIUS = 6371000;

    public static Polygon parsePolygon(String json) {
        try {
            List<Map<String, Double>> points = JSON.parseObject(json, new TypeReference<List<Map<String, Double>>>() {});
            if (points == null || points.size() < 3) {
                throw new BusinessException("多边形坐标点数量不足，至少需要3个点");
            }
            List<Coordinate> coordinates = new ArrayList<>();
            for (Map<String, Double> point : points) {
                Double lng = point.get("lng");
                Double lat = point.get("lat");
                if (lng == null || lat == null) {
                    lng = point.get("longitude");
                    lat = point.get("latitude");
                }
                if (lng == null || lat == null) {
                    throw new BusinessException("多边形坐标格式错误，缺少lng/lat或longitude/latitude字段");
                }
                coordinates.add(new Coordinate(lng, lat));
            }
            Coordinate first = coordinates.get(0);
            Coordinate last = coordinates.get(coordinates.size() - 1);
            if (first.x != last.x || first.y != last.y) {
                coordinates.add(new Coordinate(first.x, first.y));
            }
            Coordinate[] coordArray = coordinates.toArray(new Coordinate[0]);
            LinearRing shell = new LinearRing(new CoordinateArraySequence(coordArray), GEOMETRY_FACTORY);
            return new Polygon(shell, null, GEOMETRY_FACTORY);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析多边形JSON失败：{}", json, e);
            throw new BusinessException("解析多边形坐标失败：" + e.getMessage());
        }
    }

    public static boolean isPointInPolygon(Double lng, Double lat, Polygon polygon) {
        if (lng == null || lat == null || polygon == null) {
            return false;
        }
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));
        return polygon.contains(point);
    }

    public static double haversineDistance(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    public static double[] calculatePolygonCenter(Polygon polygon) {
        if (polygon == null) {
            throw new BusinessException("多边形不能为空");
        }
        Geometry centroid = polygon.getCentroid();
        Coordinate coordinate = centroid.getCoordinate();
        return new double[]{coordinate.x, coordinate.y};
    }

    public static String buildPolygonJson(List<Map<String, Double>> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(points);
    }

    public static String buildPolygonJsonFromCoordinates(List<Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return null;
        }
        List<Map<String, Double>> points = new ArrayList<>();
        for (Coordinate coord : coordinates) {
            points.add(Map.of("lng", coord.x, "lat", coord.y));
        }
        return JSON.toJSONString(points);
    }
}
