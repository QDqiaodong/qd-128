package com.example.locker.dto;

import lombok.Data;

@Data
public class StatusChangeRequest {

    /** 目标状态: ACTIVE / TEMPORARILY_DISABLED / PERMANENTLY_DISABLED */
    private String targetStatus;

    /** 变更原因 */
    private String reason;

    /** 操作人 */
    private String operator;
}
