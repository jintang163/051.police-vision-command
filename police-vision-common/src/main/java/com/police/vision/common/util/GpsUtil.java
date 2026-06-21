package com.police.vision.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class GpsUtil {

    private static final double EARTH_RADIUS = 6371.0;

    private GpsUtil() {}

    public static double getDistance(BigDecimal lng1, BigDecimal lat1, BigDecimal lng2, BigDecimal lat2) {
        if (lng1 == null || lat1 == null || lng2 == null || lat2 == null) {
            return -1;
        }
        double radLat1 = Math.toRadians(lat1.doubleValue());
        double radLat2 = Math.toRadians(lat2.doubleValue());
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1.doubleValue()) - Math.toRadians(lng2.doubleValue());
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * EARTH_RADIUS;
        return BigDecimal.valueOf(s).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    public static double getDistance(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * EARTH_RADIUS;
        return BigDecimal.valueOf(s).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    public static boolean isInRadius(BigDecimal lng1, BigDecimal lat1,
                                     BigDecimal lng2, BigDecimal lat2, double radiusKm) {
        double distance = getDistance(lng1, lat1, lng2, lat2);
        return distance >= 0 && distance <= radiusKm;
    }

    public static BigDecimal[] calculateOffset(BigDecimal lng, BigDecimal lat,
                                               double distanceKm, double bearingDegree) {
        double radLat = Math.toRadians(lat.doubleValue());
        double radLng = Math.toRadians(lng.doubleValue());
        double radBearing = Math.toRadians(bearingDegree);
        double angularDistance = distanceKm / EARTH_RADIUS;

        double newLat = Math.asin(Math.sin(radLat) * Math.cos(angularDistance)
                + Math.cos(radLat) * Math.sin(angularDistance) * Math.cos(radBearing));
        double newLng = radLng + Math.atan2(
                Math.sin(radBearing) * Math.sin(angularDistance) * Math.cos(radLat),
                Math.cos(angularDistance) - Math.sin(radLat) * Math.sin(newLat));

        return new BigDecimal[] {
                BigDecimal.valueOf(Math.toDegrees(newLng)).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(Math.toDegrees(newLat)).setScale(6, RoundingMode.HALF_UP)
        };
    }
}
