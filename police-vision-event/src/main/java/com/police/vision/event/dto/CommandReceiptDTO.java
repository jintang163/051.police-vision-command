package com.police.vision.event.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CommandReceiptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long commandId;

    private String commandNo;

    private String receiptType;

    private Long operatorId;

    private String operatorName;

    private String operatorDept;

    private String deviceId;

    private String appVersion;

    private String feedbackContent;

    private List<String> feedbackAttachments;

    private Double lng;

    private Double lat;

    private String locationDesc;

    private Long timestamp;

    private String extraData;
}
