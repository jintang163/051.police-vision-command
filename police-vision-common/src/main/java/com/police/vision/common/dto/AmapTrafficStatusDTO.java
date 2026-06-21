package com.police.vision.common.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmapTrafficStatusDTO implements Serializable {

    private Integer status;
    private String info;
    private String infocode;

    private TrafficEvaluation evaluation;

    @Data
    public static class TrafficEvaluation {
        private String status;
        private String description;
        private String expedite;
        private String congested;
        private String blocked;
        private String unknown;
        private List<RoadTraffic> roads;
    }

    @Data
    public static class RoadTraffic {
        private String name;
        private String direction;
        private String from;
        private String to;
        private String length;
        private String speed;
        private String status;
    }

    public int getTrafficLevel() {
        if (evaluation == null || evaluation.status == null) return 0;
        try {
            return Integer.parseInt(evaluation.status);
        } catch (Exception e) {
            return 0;
        }
    }
}
