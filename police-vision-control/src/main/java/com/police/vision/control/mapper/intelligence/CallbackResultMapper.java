package com.police.vision.control.mapper.intelligence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.intelligence.CallbackResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CallbackResultMapper extends BaseMapper<CallbackResult> {

    CallbackResult selectByTaskId(@Param("taskId") String taskId);
}
