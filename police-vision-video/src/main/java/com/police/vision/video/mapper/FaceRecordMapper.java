package com.police.vision.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageParam;
import com.police.vision.video.entity.FaceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FaceRecordMapper extends BaseMapper<FaceRecord> {

    @Select("SELECT * FROM face_record WHERE record_id = #{recordId} AND deleted = 0")
    FaceRecord selectByRecordId(@Param("recordId") String recordId);

    @Select("SELECT * FROM face_record WHERE camera_id = #{cameraId} AND deleted = 0 ORDER BY detect_time DESC LIMIT #{limit}")
    List<FaceRecord> selectByCameraId(@Param("cameraId") String cameraId, @Param("limit") Integer limit);

    @Select("SELECT * FROM face_record WHERE person_id = #{personId} AND deleted = 0 ORDER BY detect_time DESC")
    List<FaceRecord> selectByPersonId(@Param("personId") String personId);

    @Select("SELECT COUNT(*) FROM face_record WHERE detect_time BETWEEN #{startTime} AND #{endTime} AND deleted = 0")
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    Page<FaceRecord> selectFaceRecordPage(Page<FaceRecord> page, @Param("param") PageParam param,
                                          @Param("cameraId") String cameraId, @Param("personId") String personId,
                                          @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
