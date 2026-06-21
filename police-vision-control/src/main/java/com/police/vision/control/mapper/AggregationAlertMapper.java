package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.AggregationAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AggregationAlertMapper extends BaseMapper<AggregationAlert> {

    AggregationAlert selectByAlertId(@Param("alertId") String alertId);

    List<AggregationAlert> selectByStatus(@Param("status") Integer status);

    List<AggregationAlert> selectByAreaCode(@Param("areaCode") String areaCode);

    List<AggregationAlert> selectByTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
