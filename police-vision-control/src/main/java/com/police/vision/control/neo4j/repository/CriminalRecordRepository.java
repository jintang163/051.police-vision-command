package com.police.vision.control.neo4j.repository;

import com.police.vision.control.neo4j.node.CriminalRecordNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CriminalRecordRepository extends Neo4jRepository<CriminalRecordNode, Long> {

    CriminalRecordNode findByRecordId(String recordId);

    @Query("MATCH (p:TargetPerson {personId: $personId})-[:HAS_RECORD]->(r:CriminalRecord) " +
           "RETURN r ORDER BY r.caseDate DESC")
    List<CriminalRecordNode> findByPersonId(@Param("personId") String personId);

    List<CriminalRecordNode> findByCaseType(String caseType);

    @Query("MATCH (p:TargetPerson)-[:CO_CASE {caseId: $caseId}]->(connected:TargetPerson) " +
           "RETURN DISTINCT p.personId as personId, p.personName as personName, connected.personId as connectedId, connected.personName as connectedName")
    List<Object[]> findCaseParticipants(@Param("caseId") String caseId);
}
