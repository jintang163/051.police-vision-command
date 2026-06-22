package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.ModelTrainingJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModelTrainingJobMapper extends BaseMapper<ModelTrainingJob> {

    ModelTrainingJob selectByJobId(@Param("jobId") String jobId);

    ModelTrainingJob selectLatestDeployedModel(@Param("modelType") String modelType);

    List<ModelTrainingJob> selectByModelType(@Param("modelType") String modelType, @Param("limit") Integer limit);

    Double getLatestAccuracyEstimate(@Param("modelType") String modelType);

    int updateDeployedStatus(@Param("modelVersion") String modelVersion, @Param("deployed") Integer deployed);
}
