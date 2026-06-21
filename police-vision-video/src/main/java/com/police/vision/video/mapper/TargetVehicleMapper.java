package com.police.vision.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageParam;
import com.police.vision.video.entity.TargetVehicle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TargetVehicleMapper extends BaseMapper<TargetVehicle> {

    @Select("SELECT * FROM target_vehicle WHERE vehicle_id = #{vehicleId} AND deleted = 0")
    TargetVehicle selectByVehicleId(@Param("vehicleId") String vehicleId);

    @Select("SELECT * FROM target_vehicle WHERE plate_no = #{plateNo} AND deleted = 0")
    TargetVehicle selectByPlateNo(@Param("plateNo") String plateNo);

    @Select("SELECT * FROM target_vehicle WHERE status = #{status} AND deleted = 0")
    List<TargetVehicle> selectByStatus(@Param("status") Integer status);

    @Select("SELECT * FROM target_vehicle WHERE control_level = #{controlLevel} AND deleted = 0")
    List<TargetVehicle> selectByControlLevel(@Param("controlLevel") Integer controlLevel);

    Page<TargetVehicle> selectTargetVehiclePage(Page<TargetVehicle> page, @Param("param") PageParam param,
                                                @Param("keyword") String keyword, @Param("status") Integer status,
                                                @Param("controlLevel") Integer controlLevel);
}
