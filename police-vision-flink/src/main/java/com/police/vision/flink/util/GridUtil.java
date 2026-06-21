package com.police.vision.flink.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class GridUtil {

    private static final double GRID_SIZE = 0.001;

    public static String getGridCode(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return "unknown";
        }
        double gridLng = Math.floor(lng.doubleValue() / GRID_SIZE) * GRID_SIZE;
        double gridLat = Math.floor(lat.doubleValue() / GRID_SIZE) * GRID_SIZE;

        BigDecimal lngGrid = BigDecimal.valueOf(gridLng).setScale(6, RoundingMode.HALF_UP);
        BigDecimal latGrid = BigDecimal.valueOf(gridLat).setScale(6, RoundingMode.HALF_UP);

        return lngGrid + "_" + latGrid;
    }

    public static BigDecimal getGridCenterLng(String gridCode) {
        if (gridCode == null || !gridCode.contains("_")) {
            return BigDecimal.ZERO;
        }
        String[] parts = gridCode.split("_");
        return new BigDecimal(parts[0]).add(BigDecimal.valueOf(GRID_SIZE / 2));
    }

    public static BigDecimal getGridCenterLat(String gridCode) {
        if (gridCode == null || !gridCode.contains("_")) {
            return BigDecimal.ZERO;
        }
        String[] parts = gridCode.split("_");
        return new BigDecimal(parts[1]).add(BigDecimal.valueOf(GRID_SIZE / 2));
    }
}
