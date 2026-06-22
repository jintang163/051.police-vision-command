package com.police.vision.control.mapper.intelligence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.intelligence.HotspotPrediction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface HotspotPredictionMapper extends BaseMapper<HotspotPrediction> {

    List<Map<String, Object>> selectHistoryCaseData(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("caseType") String caseType,
            @Param("areaCode") String areaCode);

    List<Map<String, Object>> selectActualCaseData(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("caseType") String caseType,
            @Param("areaCode") String areaCode);
}
