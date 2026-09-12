package com.example.locker.dto;

import lombok.Data;

/**
 * 关闭未关闭的当面催领记录。
 */
@Data
public class ClearanceUrgeCloseRequest {

    /** 关闭说明（选填） */
    private String closeNote;

    /** 关闭经办人（选填，留空默认“系统管理员”） */
    private String closeOperator;
}
