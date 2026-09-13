package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 钥匙交接班点名明细（一条未还借用记录） */
@Data
public class KeyHandoverItemDTO {

    private Long id;

    private Long recordId;

    private Long lockerId;

    private String lockerNo;

    /** 借用台账编号 */
    private String recordNo;

    private String buildingName;

    private String unitName;

    private String floor;

    /** 点名时借出人快照 */
    private String borrower;

    private String reason;

    private LocalDateTime borrowTime;

    private LocalDateTime expectedReturnTime;

    /** 该单当前是否仍未还：交接不改借用状态，正常应为 true；已还是之后归还的结果 */
    private Boolean onLoan;
}
