package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.TargetPerson;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TargetPersonMapper extends BaseMapper<TargetPerson> {

    TargetPerson selectByPersonId(@Param("personId") String personId);

    TargetPerson selectByIdCardNo(@Param("idCardNo") String idCardNo);

    List<TargetPerson> selectByStatus(@Param("status") Integer status);

    List<TargetPerson> selectByPersonType(@Param("personType") String personType);

    List<TargetPerson> selectByStationCode(@Param("stationCode") String policeStationCode);
}
