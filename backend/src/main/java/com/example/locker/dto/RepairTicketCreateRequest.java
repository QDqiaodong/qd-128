package com.example.locker.dto;

import lombok.Data;

@Data
public class RepairTicketCreateRequest {

    private Long lockerId;
    /** 故障格口编号（必填） */
    private String compartmentNo;
    /** 故障现象（必填） */
    private String symptom;
    /** 报修人（必填） */
    private String reporter;
    private String remark;
}
