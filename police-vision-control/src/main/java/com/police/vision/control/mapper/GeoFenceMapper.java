package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.GeoFence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface GeoFenceMapper extends BaseMapper<GeoFence> {

    GeoFence selectByFenceId(@Param("fenceId") String fenceId);

    List<GeoFence> selectByEnabled(@Param("enabled") Boolean enabled);

    List<GeoFence> selectByStationCode(@Param("stationCode") String policeStationCode);

    List<GeoFence> selectByLocationAndRadius(
            @Param("longitude") BigDecimal longitude,
            @Param("latitude") BigDecimal latitude,
            @Param("radius") BigDecimal radius);
}
