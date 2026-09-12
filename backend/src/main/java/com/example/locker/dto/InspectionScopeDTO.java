package com.example.locker.dto;

import lombok.Data;

import java.util.List;

/**
 * 巡检发起页按楼栋/单元实时计算的可选范围。
 */
@Data
public class InspectionScopeDTO {

    private Long buildingId;
    private String buildingName;
    private Long unitId;
    private String unitName;
    private Integer totalLockers;
    private List<InspectionLockerOptionDTO> lockers;
}
