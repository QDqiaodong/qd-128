package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登记一笔当面催领：催领时间与经办人必填。
 */
@Data
public class ClearanceUrgeCreateRequest {

    /** 催领时间（不填默认当前时间；补登可填过去时间，不能晚于当前时间） */
    private LocalDateTime urgeTime;

    /** 经办人（必填） */
    private String operator;
}
