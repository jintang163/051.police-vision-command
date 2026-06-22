package com.police.vision.control.mapper.intelligence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.intelligence.PublicOpinion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PublicOpinionMapper extends BaseMapper<PublicOpinion> {

    int cleanOldOpinions(int days, int limit);
}
