package com.police.vision.control.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SentimentResultDTO implements Serializable {

    private String sentimentLabel;

    private Double sentimentScore;

    private List<String> keywords;

    private List<String> topics;

    private String summary;
}
