package com.example.locker.dto;

import lombok.Data;

@Data
public class RepairTicketCompleteRequest {

    /** 处理人（完工必填） */
    private String handler;
    /** 处理结果（完工必填） */
    private String repairResult;
}
