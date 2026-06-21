package com.police.vision.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageParam;
import com.police.vision.video.entity.PlateRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PlateRecordMapper extends BaseMapper<PlateRecord> {

    @Select("SELECT * FROM plate_record WHERE record_id = #{recordId} AND deleted = 0")
    PlateRecord selectByRecordId(@Param("recordId") String recordId);

    @Select("SELECT * FROM plate_record WHERE camera_id = #{cameraId} AND deleted = 0 ORDER BY detect_time DESC LIMIT #{limit}")
    List<PlateRecord> selectByCameraId(@Param("cameraId") String cameraId, @Param("limit") Integer limit);

    @Select("SELECT * FROM plate_record WHERE plate_no = #{plateNo} AND deleted = 0 ORDER BY detect_time DESC")
    List<PlateRecord> selectByPlateNo(@Param("plateNo") String plateNo);

    @Select("SELECT COUNT(*) FROM plate_record WHERE detect_time BETWEEN #{startTime} AND #{endTime} AND deleted = 0")
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    Page<PlateRecord> selectPlateRecordPage(Page<PlateRecord> page, @Param("param") PageParam param,
                                            @Param("cameraId") String cameraId, @Param("plateNo") String plateNo,
                                            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
