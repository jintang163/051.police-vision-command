package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.FenceAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FenceAlertMapper extends BaseMapper<FenceAlert> {

    FenceAlert selectByAlertId(@Param("alertId") String alertId);

    List<FenceAlert> selectByPersonId(@Param("personId") String personId);

    List<FenceAlert> selectByFenceId(@Param("fenceId") String fenceId);

    List<FenceAlert> selectByStatus(@Param("status") Integer status);

    FenceAlert selectActiveAlert(@Param("personId") String personId, @Param("fenceId") String fenceId);

    List<FenceAlert> selectByTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
