package com.police.vision.event.feign;

import com.police.vision.common.result.Result;
import com.police.vision.event.entity.vo.CameraPointVO;
import com.police.vision.event.entity.vo.PoliceLocationVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "police-vision-gis", path = "/gis")
public interface GisFeignClient {

    @GetMapping("/police/distribution")
    Result<List<PoliceLocationVO>> getPoliceDistribution();

    @GetMapping("/camera/points")
    Result<List<CameraPointVO>> getCameraPoints();

    @GetMapping("/police/nearby")
    Result<List<PoliceLocationVO>> getNearbyPolice(
            @RequestParam BigDecimal lng,
            @RequestParam BigDecimal lat,
            @RequestParam Double radiusKm);
}
