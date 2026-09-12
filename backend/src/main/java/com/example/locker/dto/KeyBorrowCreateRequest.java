package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登记钥匙借用请求
 */
@Data
public class KeyBorrowCreateRequest {

    private Long lockerId;
    /** 借出人 */
    private String borrower;
    /** 借用事由 */
    private String reason;
    /** 借出时间（历史补登可为过去时间） */
    private LocalDateTime borrowTime;
    /** 预计归还时间 */
    private LocalDateTime expectedReturnTime;
    private String remark;
}
