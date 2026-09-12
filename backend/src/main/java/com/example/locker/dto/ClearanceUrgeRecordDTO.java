package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当面催领记录
 */
@Data
public class ClearanceUrgeRecordDTO {

    private Long id;
    private Long orderId;
    private LocalDateTime urgeTime;
    private String operator;
    /** OPEN-未关闭, CLOSED-已关闭 */
    private String status;
    private String statusName;
    private String closeNote;
    private String closeOperator;
    private LocalDateTime closeTime;
    /** 办结时系统自动关闭 */
    private Boolean autoClosed;
    private LocalDateTime createTime;
}
