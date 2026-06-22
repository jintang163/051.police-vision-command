package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.PersonTrackPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PersonTrackPointMapper extends BaseMapper<PersonTrackPoint> {

    List<PersonTrackPoint> selectByPersonIdAndTimeRange(
            @Param("personId") String personId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<PersonTrackPoint> selectRecentByPersonId(
            @Param("personId") String personId,
            @Param("days") Integer days,
            @Param("limit") Integer limit
    );

    List<PersonTrackPoint> selectRealtimeByPersonIds(
            @Param("personIds") List<String> personIds,
            @Param("minutes") Integer minutes
    );

    int batchInsertTrackPoints(@Param("list") List<PersonTrackPoint> list);

    int cleanOldTrackPoints(@Param("days") Integer days, @Param("limit") Integer limit);

    List<Map<String, Object>> getTrackStatistics(
            @Param("personId") String personId,
            @Param("days") Integer days
    );
}
