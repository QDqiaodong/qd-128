package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class LockerDTO {

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
    private LocalDate installationDate;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
