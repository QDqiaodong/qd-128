package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按柜钥匙状态一览：全部柜体列出，借用中的柜体置顶，
 * 未还条数与柜体标记均实时由借用台账推导
 */
@Data
public class LockerKeyBorrowOverviewDTO {

    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    /** 柜体当前生命周期状态 */
    private String status;
    private String statusName;
    /** 是否有借用中（未归还）的记录 */
    private Boolean onLoan;
    /** 未还条数（借用中记录数） */
    private Integer openRecordCount;
    /** 历史借用记录总数 */
    private Integer totalRecordCount;
    /** 最近一次借出时间 */
    private LocalDateTime lastBorrowTime;
}
