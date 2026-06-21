package com.police.vision.traffic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.common.entity.VehicleControl;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VehicleControlMapper extends BaseMapper<VehicleControl> {

    List<VehicleControl> selectActiveControls();

    List<VehicleControl> selectByPlateNo(@Param("plateNo") String plateNo);

    List<VehicleControl> selectByCondition(@Param("areaCode") String areaCode,
                                           @Param("vehicleType") String vehicleType,
                                           @Param("vehicleColor") String vehicleColor,
                                           @Param("status") Integer status);
}
