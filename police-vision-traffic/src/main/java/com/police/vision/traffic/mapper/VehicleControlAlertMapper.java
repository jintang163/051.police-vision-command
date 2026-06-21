package com.police.vision.traffic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.common.entity.VehicleControlAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VehicleControlAlertMapper extends BaseMapper<VehicleControlAlert> {

    List<VehicleControlAlert> selectByPlateNoAndTime(@Param("plateNo") String plateNo,
                                                      @Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    List<VehicleControlAlert> selectByControlId(@Param("controlId") String controlId);

    int countTodayByPlateNo(@Param("plateNo") String plateNo);
}
