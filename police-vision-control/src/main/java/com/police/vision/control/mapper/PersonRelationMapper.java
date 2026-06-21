package com.police.vision.control.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.police.vision.control.entity.PersonRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PersonRelationMapper extends BaseMapper<PersonRelation> {

    @Select("SELECT * FROM person_relation WHERE (person_id1 = #{personId} OR person_id2 = #{personId}) AND deleted = 0 ORDER BY strength DESC")
    List<PersonRelation> selectByPersonId(@Param("personId") String personId);

    @Select("SELECT * FROM person_relation WHERE (person_id1 = #{personId} OR person_id2 = #{personId}) AND relation_type = #{relationType} AND deleted = 0 ORDER BY strength DESC")
    List<PersonRelation> selectByPersonIdAndType(@Param("personId") String personId, @Param("relationType") String relationType);

    @Select("SELECT * FROM person_relation WHERE synced_to_neo4j = 0 AND deleted = 0 LIMIT #{limit}")
    List<PersonRelation> selectUnsyncedToNeo4j(@Param("limit") int limit);
}
