package com.police.vision.common.entity;

import lombok.Data;
import java.io.Serializable;

@Data
public class PageParam implements Serializable {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String orderBy;
    private String orderDirection = "desc";

    public Integer getPageNum() {
        return pageNum != null && pageNum > 0 ? pageNum : 1;
    }

    public Integer getPageSize() {
        if (pageSize == null || pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
