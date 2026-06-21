package com.police.vision.control.neo4j.node;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("CriminalRecord")
public class CriminalRecordNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property(name = "recordId")
    private String recordId;

    @Property(name = "caseType")
    private String caseType;

    @Property(name = "caseName")
    private String caseName;

    @Property(name = "caseDate")
    private LocalDate caseDate;

    @Property(name = "caseLocation")
    private String caseLocation;

    @Property(name = "penalty")
    private String penalty;

    @Property(name = "severity")
    private Integer severity;

    @Property(name = "description")
    private String description;
}
