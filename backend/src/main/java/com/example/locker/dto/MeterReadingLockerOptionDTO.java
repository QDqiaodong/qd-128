package com.example.locker.dto;

import lombok.Data;

/**
 * 登记抄表的可选柜体：包含全部生命周期状态的柜体；
 * 本月已存在有效抄表单的柜体置灰，防止同一柜同一自然月挂两张未作废单
 */
@Data
public class MeterReadingLockerOptionDTO {

    private Long id;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String status;
    private String statusName;
    /** 本月是否已抄（已抄的柜体在登记下拉框中置灰） */
    private Boolean readThisMonth;
}
