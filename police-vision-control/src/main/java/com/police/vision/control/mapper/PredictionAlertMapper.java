package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.PredictionAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PredictionAlertMapper extends BaseMapper<PredictionAlert> {

    PredictionAlert selectByAlertId(@Param("alertId") String alertId);

    List<PredictionAlert> selectByStatus(@Param("status") Integer status);

    List<PredictionAlert> selectByPersonId(@Param("personId") String personId);

    List<PredictionAlert> selectByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("alertLevel") Integer alertLevel
    );

    List<PredictionAlert> selectUnhandledHighRisk(
            @Param("alertLevel") Integer minAlertLevel
    );

    int updateCrowdDataFromAggregation();
}
