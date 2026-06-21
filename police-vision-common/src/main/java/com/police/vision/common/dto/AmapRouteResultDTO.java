package com.police.vision.common.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmapRouteResultDTO implements Serializable {

    private Integer status;

    private String info;

    private String infocode;

    private RouteInfo route;

    @Data
    public static class RouteInfo {
        private String origin;
        private String destination;
        private List<PathInfo> paths;
    }

    @Data
    public static class PathInfo {
        private String distance;
        private String duration;
        private String strategy;
        private String trafficLights;
        private List<StepInfo> steps;
        private List<TmcInfo> tmcs;
    }

    @Data
    public static class StepInfo {
        private String instruction;
        private String distance;
        private String duration;
        private String road;
        private String polyline;
        private String action;
    }

    @Data
    public static class TmcInfo {
        private String distance;
        private String status;
        private String polyline;
    }

    public BigDecimal getFirstPathDistance() {
        if (route != null && route.paths != null && !route.paths.isEmpty()) {
            try {
                return new BigDecimal(route.paths.get(0).distance);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public Integer getFirstPathDuration() {
        if (route != null && route.paths != null && !route.paths.isEmpty()) {
            try {
                return Integer.parseInt(route.paths.get(0).duration);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public double getAvgTrafficLevel() {
        if (route != null && route.paths != null && !route.paths.isEmpty()
                && route.paths.get(0).tmcs != null && !route.paths.get(0).tmcs.isEmpty()) {
            int total = 0;
            int count = 0;
            for (TmcInfo tmc : route.paths.get(0).tmcs) {
                try {
                    total += Integer.parseInt(tmc.status);
                    count++;
                } catch (Exception ignored) {}
            }
            return count > 0 ? (double) total / count : 0;
        }
        return 0;
    }
}
