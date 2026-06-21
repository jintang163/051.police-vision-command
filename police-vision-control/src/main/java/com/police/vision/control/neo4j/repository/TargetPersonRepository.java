package com.police.vision.control.neo4j.repository;

import com.police.vision.control.neo4j.node.TargetPersonNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TargetPersonRepository extends Neo4jRepository<TargetPersonNode, Long> {

    Optional<TargetPersonNode> findByPersonId(String personId);

    Optional<TargetPersonNode> findByIdCardNo(String idCardNo);

    List<TargetPersonNode> findByPersonType(String personType);

    List<TargetPersonNode> findByControlLevel(Integer controlLevel);

    @Query("MATCH (p:TargetPerson {personId: $personId})-[r*1..2]-(connected:TargetPerson) " +
           "RETURN DISTINCT connected, r LIMIT $limit")
    List<TargetPersonNode> findRelatedPersons(@Param("personId") String personId, @Param("limit") int limit);

    @Query("MATCH (p1:TargetPerson {personId: $personId1})-[r:CO_CASE]-(p2:TargetPerson {personId: $personId2}) " +
           "RETURN count(r) > 0")
    boolean hasCoCaseRelation(@Param("personId1") String personId1, @Param("personId2") String personId2);

    @Query("MATCH (p:TargetPerson) WHERE p.personName CONTAINS $keyword OR p.idCardNo CONTAINS $keyword " +
           "RETURN p LIMIT $limit")
    List<TargetPersonNode> searchPersons(@Param("keyword") String keyword, @Param("limit") int limit);

    @Query("MATCH (p:TargetPerson) " +
           "WHERE p.riskScore IS NOT NULL AND p.riskScore >= $minScore " +
           "RETURN p ORDER BY p.riskScore DESC LIMIT $limit")
    List<TargetPersonNode> findHighRiskPersons(@Param("minScore") double minScore, @Param("limit") int limit);

    @Query("MATCH (p:TargetPerson)-[r:FREQUENT_CONTACT]->(connected:TargetPerson) " +
           "WHERE p.personId = $personId " +
           "RETURN connected, r ORDER BY r.contactCount DESC LIMIT $limit")
    List<TargetPersonNode> findFrequentContacts(@Param("personId") String personId, @Param("limit") int limit);

    @Query("MATCH (p:TargetPerson)-[r:CO_CASE]-(connected:TargetPerson) " +
           "WHERE p.personId = $personId " +
           "RETURN connected, r ORDER BY r.caseDate DESC LIMIT $limit")
    List<TargetPersonNode> findCoCasePersons(@Param("personId") String personId, @Param("limit") int limit);

    @Query("MATCH (p:TargetPerson {personId: $personId})-[r*1..3]-(gang:TargetPerson) " +
           "RETURN DISTINCT gang, r LIMIT $limit")
    List<TargetPersonNode> findGangMembers(@Param("personId") String personId, @Param("limit") int limit);

    @Query("MATCH (p:TargetPerson)-[:CO_CASE*2..5]-(connected:TargetPerson) " +
           "WHERE p.personId = $personId " +
           "WITH connected, count(DISTINCT r) as depth ORDER BY depth ASC LIMIT $limit " +
           "RETURN connected")
    List<TargetPersonNode> findExtendedRelations(@Param("personId") String personId, @Param("limit") int limit);
}
