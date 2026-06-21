package com.police.vision.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageParam;
import com.police.vision.video.entity.CameraDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CameraDeviceMapper extends BaseMapper<CameraDevice> {

    @Select("SELECT * FROM camera_device WHERE device_id = #{deviceId} AND deleted = 0")
    CameraDevice selectByDeviceId(@Param("deviceId") String deviceId);

    @Select("SELECT * FROM camera_device WHERE status = #{status} AND deleted = 0")
    List<CameraDevice> selectByStatus(@Param("status") Integer status);

    @Select("SELECT * FROM camera_device WHERE region = #{region} AND deleted = 0")
    List<CameraDevice> selectByRegion(@Param("region") String region);

    Page<CameraDevice> selectCameraPage(Page<CameraDevice> page, @Param("param") PageParam param,
                                        @Param("keyword") String keyword, @Param("status") Integer status,
                                        @Param("region") String region);
}
