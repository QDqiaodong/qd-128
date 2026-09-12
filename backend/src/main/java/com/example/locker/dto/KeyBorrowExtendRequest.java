package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 借用改期请求：仅当钥匙未还且预计归还已到期（已逾期）时，才允许在原借用单上延后预计归还时间。
 * 新的预计归还时间必填且必须晚于原预计归还时间；改期原因必填。
 */
@Data
public class KeyBorrowExtendRequest {

    /** 新的预计归还时间（必须晚于原预计归还时间） */
    private LocalDateTime expectedReturnTime;

    /** 改期原因（必填） */
    private String extendReason;
}
