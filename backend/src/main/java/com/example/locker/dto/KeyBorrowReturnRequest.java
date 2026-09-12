package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归还钥匙请求：归还人必填；归还时间可不传（默认当前时间），
 * 补登历史归还时可指定过去的归还时间。
 */
@Data
public class KeyBorrowReturnRequest {

    private String returner;
    private LocalDateTime returnTime;
}
