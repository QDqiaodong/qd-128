package com.example.locker.dto;

import lombok.Data;

@Data
public class ReassignRequest {

    /** 新负责人 */
    private String newAssignee;

    /** 转派原因（必填） */
    private String reason;

    /** 操作人（选填） */
    private String operator;
}
