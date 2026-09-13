package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交接窗口打开时的待点名清单：itemCount 为当前全部未还记录条数，
 * 提交时勾选数量必须与之一致，缺一条都不能交班。
 */
@Data
public class KeyHandoverPendingItemDTO {

    /** 借用台账记录 ID，勾选时回传 */
    private Long recordId;

    private String recordNo;

    private Long lockerId;

    private String lockerNo;

    private String buildingName;

    private String unitName;

    private String floor;

    /** 当前借出人 */
    private String borrower;

    private String reason;

    private LocalDateTime borrowTime;

    private LocalDateTime expectedReturnTime;

    /** 预计归还已过 */
    private Boolean overdue;
}
