package com.example.locker.dto;

import lombok.Data;

@Data
public class IssueHandleRequest {

    /** 目标状态：PROCESSING/RESOLVED */
    private String status;

    private String handler;

    private String handleNote;
}
