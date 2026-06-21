package com.police.vision.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageParam;
import com.police.vision.video.entity.TargetPerson;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TargetPersonMapper extends BaseMapper<TargetPerson> {

    @Select("SELECT * FROM target_person WHERE person_id = #{personId} AND deleted = 0")
    TargetPerson selectByPersonId(@Param("personId") String personId);

    @Select("SELECT * FROM target_person WHERE id_card_no = #{idCardNo} AND deleted = 0")
    TargetPerson selectByIdCardNo(@Param("idCardNo") String idCardNo);

    @Select("SELECT * FROM target_person WHERE status = #{status} AND deleted = 0")
    List<TargetPerson> selectByStatus(@Param("status") Integer status);

    @Select("SELECT * FROM target_person WHERE control_level = #{controlLevel} AND deleted = 0")
    List<TargetPerson> selectByControlLevel(@Param("controlLevel") Integer controlLevel);

    Page<TargetPerson> selectTargetPersonPage(Page<TargetPerson> page, @Param("param") PageParam param,
                                              @Param("keyword") String keyword, @Param("status") Integer status,
                                              @Param("controlLevel") Integer controlLevel);
}
