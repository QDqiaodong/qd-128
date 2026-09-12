package com.example.locker.dto;

import lombok.Data;

/**
 * 发起巡检时可选择的正常柜体。
 */
@Data
public class InspectionLockerOptionDTO {

    private Long id;
    private String lockerNo;
    private Integer compartmentCount;
    private String specType;
    private String specTypeName;
    private Long buildingId;
    private String buildingName;
    private Long unitId;
    private String unitName;
    private String floor;
}
