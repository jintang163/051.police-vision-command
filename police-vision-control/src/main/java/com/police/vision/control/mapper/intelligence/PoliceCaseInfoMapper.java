package com.police.vision.control.mapper.intelligence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.intelligence.PoliceCaseInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PoliceCaseInfoMapper extends BaseMapper<PoliceCaseInfo> {

    @Select("<script>" +
            "SELECT case_type AS case_type, COUNT(1) AS cnt " +
            "FROM police_case_info " +
            "WHERE case_time &gt;= #{startTime} AND case_time &lt; #{endTime} " +
            "<if test='caseType != null and caseType != \"\"'> AND case_type = #{caseType} </if>" +
            "<if test='areaCode != null and areaCode != \"\"'> AND area_code = #{areaCode} </if>" +
            "GROUP BY case_type ORDER BY cnt DESC" +
            "</script>")
    List<Map<String, Object>> selectCaseTypeDistribution(@Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime,
                                                          @Param("caseType") String caseType,
                                                          @Param("areaCode") String areaCode);

    @Select("<script>" +
            "SELECT COUNT(1) FROM police_case_info " +
            "WHERE case_time &gt;= #{startTime} AND case_time &lt; #{endTime} " +
            "<if test='caseType != null and caseType != \"\"'> AND case_type = #{caseType} </if>" +
            "<if test='areaCode != null and areaCode != \"\"'> AND area_code = #{areaCode} </if>" +
            "</script>")
    int countByTimeRange(@Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime,
                         @Param("caseType") String caseType,
                         @Param("areaCode") String areaCode);

    @Select("<script>" +
            "SELECT COUNT(1) FROM police_case_info " +
            "WHERE case_time &gt;= #{startTime} AND case_time &lt; #{endTime} " +
            "AND is_solved = 1 " +
            "<if test='caseType != null and caseType != \"\"'> AND case_type = #{caseType} </if>" +
            "<if test='areaCode != null and areaCode != \"\"'> AND area_code = #{areaCode} </if>" +
            "</script>")
    int countSolvedByTimeRange(@Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime,
                               @Param("caseType") String caseType,
                               @Param("areaCode") String areaCode);

    @Select("<script>" +
            "SELECT * FROM police_case_info " +
            "WHERE case_time &gt;= #{startTime} AND case_time &lt; #{endTime} " +
            "<if test='caseType != null and caseType != \"\"'> AND case_type = #{caseType} </if>" +
            "<if test='areaCode != null and areaCode != \"\"'> AND area_code LIKE CONCAT(#{areaCode}, '%') </if>" +
            "ORDER BY case_time DESC LIMIT #{limit}" +
            "</script>")
    List<PoliceCaseInfo> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("caseType") String caseType,
                                            @Param("areaCode") String areaCode,
                                            @Param("limit") int limit);

    List<PoliceCaseInfo> selectClosedCasesWithoutCallback(@Param("delayHours") int delayHours,
                                                           @Param("lookbackHours") int lookbackHours,
                                                           @Param("limit") int limit);
}
