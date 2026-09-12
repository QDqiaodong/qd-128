package com.example.locker.dto;

import lombok.Data;

/**
 * 钥匙借用登记可选柜体：包含全部生命周期状态（永久停用柜可补登历史借用）
 */
@Data
public class KeyBorrowLockerOptionDTO {

    private Long id;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String status;
    private String statusName;
    /** 当前是否有未还借用记录 */
    private Boolean onLoan;
}
