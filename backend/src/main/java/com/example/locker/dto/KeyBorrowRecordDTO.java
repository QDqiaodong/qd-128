package com.example.locker.dto;

import com.example.locker.enums.KeyBorrowStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KeyBorrowRecordDTO {

    private Long id;
    private String recordNo;
    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    /** 柜体当前生命周期状态（永久停用柜的历史借用仍可查看） */
    private String lockerStatus;
    private String lockerStatusName;
    /** 借出人 */
    private String borrower;
    /** 借用事由 */
    private String reason;
    private LocalDateTime borrowTime;
    private LocalDateTime expectedReturnTime;
    private KeyBorrowStatus status;
    private String statusName;
    /** 借用中的记录置为 true，列表与详情据此标记「借用中」 */
    private Boolean onLoan;
    /** 借用中且预计归还时间已过 */
    private Boolean returnOverdue;
    private String returner;
    private LocalDateTime returnTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
