package com.police.vision.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageParam;
import com.police.vision.video.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {

    @Select("SELECT * FROM alert_record WHERE alert_id = #{alertId} AND deleted = 0")
    AlertRecord selectByAlertId(@Param("alertId") String alertId);

    @Select("SELECT * FROM alert_record WHERE camera_id = #{cameraId} AND deleted = 0 ORDER BY detect_time DESC LIMIT #{limit}")
    List<AlertRecord> selectByCameraId(@Param("cameraId") String cameraId, @Param("limit") Integer limit);

    @Select("SELECT alert_type, COUNT(*) as count FROM alert_record " +
            "WHERE detect_time BETWEEN #{startTime} AND #{endTime} AND deleted = 0 " +
            "GROUP BY alert_type")
    List<Map<String, Object>> countByType(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT alert_level, COUNT(*) as count FROM alert_record " +
            "WHERE detect_time BETWEEN #{startTime} AND #{endTime} AND deleted = 0 " +
            "GROUP BY alert_level")
    List<Map<String, Object>> countByLevel(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT processed, COUNT(*) as count FROM alert_record " +
            "WHERE detect_time BETWEEN #{startTime} AND #{endTime} AND deleted = 0 " +
            "GROUP BY processed")
    List<Map<String, Object>> countByProcessed(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    Page<AlertRecord> selectAlertPage(Page<AlertRecord> page, @Param("param") PageParam param,
                                      @Param("alertType") Integer alertType, @Param("alertLevel") Integer alertLevel,
                                      @Param("processed") Integer processed, @Param("cameraId") String cameraId,
                                      @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
