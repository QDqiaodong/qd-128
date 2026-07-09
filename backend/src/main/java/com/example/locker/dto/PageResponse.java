package com.example.locker.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {

    private List<T> data;
    private Long total;
    private Integer page;
    private Integer size;

    public PageResponse(List<T> data, Long total, Integer page, Integer size) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
