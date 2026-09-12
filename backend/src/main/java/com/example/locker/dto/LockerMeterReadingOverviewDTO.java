package com.example.locker.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 按柜本月抄表状态一览：全部柜体列出，未抄的柜体置顶，
 * 已抄/未抄标记与本月读数均实时由抄表单推导，抄表单是唯一数据源
 */
@Data
public class LockerMeterReadingOverviewDTO {

    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    /** 柜体当前生命周期状态 */
    private String status;
    private String statusName;
    /** 账期（自然月，格式 yyyy-MM） */
    private String periodMonth;
    /** 该账期是否已抄（存在有效抄表单） */
    private Boolean read;
    /** 已抄时的有效抄表单ID */
    private Long recordId;
    /** 本月电表读数（kWh），未抄为空 */
    private BigDecimal readingValue;
    /** 抄表人 */
    private String reader;
    /** 抄表时间 */
    private LocalDateTime readingTime;
}
