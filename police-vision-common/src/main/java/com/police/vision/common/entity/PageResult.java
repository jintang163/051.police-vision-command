package com.police.vision.common.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private Long total;
    private List<T> list;
    private Integer pageNum;
    private Integer pageSize;
    private Long pages;

    public static <T> PageResult<T> of(Long total, List<T> list, Integer pageNum, Integer pageSize) {
        long pages = total % pageSize == 0 ? total / pageSize : total / pageSize + 1;
        return new PageResult<>(total, list, pageNum, pageSize, pages);
    }
}
