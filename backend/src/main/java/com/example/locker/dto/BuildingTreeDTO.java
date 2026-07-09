package com.example.locker.dto;

import lombok.Data;

import java.util.List;

@Data
public class BuildingTreeDTO {

    private Long id;
    private String name;
    private String code;
    private List<UnitDTO> children;
}
