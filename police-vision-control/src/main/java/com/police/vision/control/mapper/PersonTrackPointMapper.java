package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.PersonTrackPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

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
}
