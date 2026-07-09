package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LockerUpdateRequest {

    private String lockerNo;
    private Integer compartmentCount;
    private String specType;
    private Long buildingId;
    private Long unitId;
    private String floor;
    private LocalDate installationDate;
    private String remark;
}
