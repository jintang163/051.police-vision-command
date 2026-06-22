package com.police.vision.control.mapper.intelligence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.intelligence.CallbackTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CallbackTaskMapper extends BaseMapper<CallbackTask> {

    List<CallbackTask> selectScheduledTasks(@Param("hours") Integer hours, @Param("limit") Integer limit);

    int updateTaskStatus(@Param("taskId") String taskId, @Param("status") Integer status, @Param("statusName") String statusName);

    Map<String, Object> countTodayTasks();

    List<Map<String, Object>> countSatisfactionByDays(@Param("days") Integer days);

    Map<String, Object> avgThreeIndicators(@Param("days") Integer days);

    List<Map<String, Object>> countByDept(@Param("days") Integer days, @Param("areaCode") String areaCode);

    List<CallbackTask> selectCallingTimeoutTasks(@Param("minutes") Integer minutes);

    List<CallbackTask> selectNeedRetryTasks(@Param("limit") Integer limit);
}
