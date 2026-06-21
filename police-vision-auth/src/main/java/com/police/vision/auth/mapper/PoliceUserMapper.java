package com.police.vision.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.auth.entity.PoliceUser;
import com.police.vision.common.entity.PageParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PoliceUserMapper extends BaseMapper<PoliceUser> {

    @Select("SELECT * FROM sys_police_user WHERE police_no = #{policeNo} AND deleted = 0")
    PoliceUser selectByPoliceNo(@Param("policeNo") String policeNo);

    @Select("SELECT r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.deleted = 0")
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    Page<PoliceUser> selectPoliceList(Page<PoliceUser> page, @Param("param") PageParam param,
                                       @Param("deptId") Long deptId, @Param("keyword") String keyword,
                                       @Param("status") Integer status);

    @Select("SELECT id, police_no, name, phone, dept_id, dept_name, status, longitude, latitude " +
            "FROM sys_police_user WHERE dept_id = #{deptId} AND deleted = 0")
    List<PoliceUser> selectByDeptId(@Param("deptId") Long deptId);

    @Select("SELECT u.id, u.police_number AS police_no, u.real_name AS name, u.phone, " +
            "u.department_id AS dept_id, d.dept_name, u.status, u.longitude, u.latitude " +
            "FROM sys_user u " +
            "LEFT JOIN sys_department d ON u.department_id = d.id " +
            "WHERE d.dept_code = #{stationCode} AND u.status = 1 AND u.deleted = 0")
    List<Map<String, Object>> selectByStationCode(@Param("stationCode") String stationCode);
}
