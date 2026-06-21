package com.police.vision.control.neo4j.node;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("TargetPerson")
public class TargetPersonNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property(name = "personId")
    private String personId;

    @Property(name = "personName")
    private String personName;

    @Property(name = "idCardNo")
    private String idCardNo;

    @Property(name = "personType")
    private String personType;

    @Property(name = "controlLevel")
    private Integer controlLevel;

    @Property(name = "address")
    private String address;

    @Property(name = "phone")
    private String phone;

    @Property(name = "gender")
    private String gender;

    @Property(name = "age")
    private Integer age;

    @Property(name = "avatarUrl")
    private String avatarUrl;

    @Property(name = "remark")
    private String remark;

    @Property(name = "riskScore")
    private Double riskScore;

    @Relationship(type = "CO_CASE", direction = Relationship.Direction.OUTGOING)
    private List<PersonRelationship> coCaseRelations = new ArrayList<>();

    @Relationship(type = "FREQUENT_CONTACT", direction = Relationship.Direction.OUTGOING)
    private List<PersonRelationship> frequentContactRelations = new ArrayList<>();

    @Relationship(type = "FAMILY", direction = Relationship.Direction.OUTGOING)
    private List<PersonRelationship> familyRelations = new ArrayList<>();

    @Relationship(type = "HAS_RECORD", direction = Relationship.Direction.OUTGOING)
    private List<CriminalRecordNode> criminalRecords = new ArrayList<>();
}
