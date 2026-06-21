package com.police.vision.alarm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.common.entity.DispatchTrafficSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DispatchTrafficSnapshotMapper extends BaseMapper<DispatchTrafficSnapshot> {

    List<DispatchTrafficSnapshot> selectByAlarmId(@Param("alarmId") Long alarmId);

    List<DispatchTrafficSnapshot> selectByDispatchId(@Param("dispatchId") Long dispatchId);

    List<DispatchTrafficSnapshot> selectByTimeRange(@Param("startTime") String startTime,
                                                     @Param("endTime") String endTime);

    DispatchTrafficSnapshot selectBySnapshotId(@Param("snapshotId") String snapshotId);
}
