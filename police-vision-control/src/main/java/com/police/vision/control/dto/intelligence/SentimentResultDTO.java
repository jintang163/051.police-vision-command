package com.police.vision.control.dto.intelligence;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SentimentResultDTO implements Serializable {

    private Integer sentimentLabel;

    private BigDecimal sentimentScore;

    private List<String> keywords;

    private List<String> topics;

    private String summary;
}
