package com.police.vision.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.util.List;

@Data
@Validated
public class TaskGroupDTO implements Serializable {

    @NotBlank(message = "小组名称不能为空")
    private String groupName;

    private String groupLeader;

    private Long groupLeaderId;

    private String description;

    @Valid
    private List<PostDTO> posts;
}
