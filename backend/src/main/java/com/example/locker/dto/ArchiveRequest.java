package com.example.locker.dto;

import lombok.Data;

@Data
public class ArchiveRequest {

    private String archiveName;
    private String filterConditions;
    private Integer resultCount;
    private String operator;
}
