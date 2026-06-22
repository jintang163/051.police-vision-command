package com.police.vision.control.mapper.intelligence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.intelligence.CrawlerTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CrawlerTaskMapper extends BaseMapper<CrawlerTask> {

    List<CrawlerTask> selectEnabledTasks();
}
