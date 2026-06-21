package com.police.vision.control.neo4j.relation;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;
import com.police.vision.control.neo4j.node.TargetPersonNode;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RelationshipProperties
public class PersonRelationship {

    @Id
    @GeneratedValue
    private Long id;

    @TargetNode
    private TargetPersonNode targetPerson;

    @Property(name = "relationType")
    private String relationType;

    @Property(name = "relationName")
    private String relationName;

    @Property(name = "strength")
    private Integer strength;

    @Property(name = "firstContactDate")
    private LocalDate firstContactDate;

    @Property(name = "lastContactDate")
    private LocalDate lastContactDate;

    @Property(name = "contactCount")
    private Integer contactCount;

    @Property(name = "caseId")
    private String caseId;

    @Property(name = "caseName")
    private String caseName;

    @Property(name = "description")
    private String description;
}
