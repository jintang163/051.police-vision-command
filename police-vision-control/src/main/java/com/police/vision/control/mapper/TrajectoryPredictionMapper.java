package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.TrajectoryPrediction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TrajectoryPredictionMapper extends BaseMapper<TrajectoryPrediction> {

    List<TrajectoryPrediction> selectLatestByPersonId(
            @Param("personId") String personId,
            @Param("limit") Integer limit
    );

    List<TrajectoryPrediction> selectByBatch(
            @Param("predictionBatch") String predictionBatch
    );

    List<TrajectoryPrediction> selectHighRiskPredictions(
            @Param("minProbability") Double minProbability,
            @Param("sensitiveOnly") Integer sensitiveOnly,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
