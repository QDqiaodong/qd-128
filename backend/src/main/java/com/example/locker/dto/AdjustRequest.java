package com.example.locker.dto;

import lombok.Data;

@Data
public class AdjustRequest {

    private Long newBuildingId;
    private Long newUnitId;
    private String reason;
    private String operator;
}
