package com.example.locker.dto;

import lombok.Data;

import java.util.List;

@Data
public class FilterRequest {

    private List<Long> buildingIds;
    private List<Long> unitIds;
    private List<String> specTypes;
    private String startDate;
    private String endDate;
    private Integer page = 1;
    private Integer size = 20;
}
