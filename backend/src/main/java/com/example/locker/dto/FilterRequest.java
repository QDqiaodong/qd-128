package com.example.locker.dto;

import lombok.Data;

import java.util.List;

@Data
public class FilterRequest {

    private List<Long> buildingIds;
    private List<Long> unitIds;
    private List<String> specTypes;
    /** 生命周期状态过滤；为 null 或空时多条件筛选默认只查正常柜体 */
    private List<String> statuses;
    private String startDate;
    private String endDate;
    private Integer page = 1;
    private Integer size = 20;
}
