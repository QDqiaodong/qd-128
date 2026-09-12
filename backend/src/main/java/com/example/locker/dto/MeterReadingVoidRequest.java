package com.example.locker.dto;

import lombok.Data;

/**
 * 作废抄表单请求
 */
@Data
public class MeterReadingVoidRequest {

    /** 作废原因（必填） */
    private String voidReason;
    /** 作废人（留空默认系统管理员） */
    private String voidOperator;
}
