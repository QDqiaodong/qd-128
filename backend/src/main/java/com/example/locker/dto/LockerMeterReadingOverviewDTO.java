package com.example.locker.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 按柜本月抄表状态一览：全部柜体列出，未抄的柜体置顶，
 * 已抄/未抄标记与本月读数均实时由抄表单推导，抄表单是唯一数据源；
 * 每行同时带出本账期之前最近一次有效抄表的读数、抄表人和抄表时间，便于现场对表
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
    /** 上次电表读数（kWh）：本账期之前最近一张有效抄表单的读数，从未抄过为空 */
    private BigDecimal lastReadingValue;
    /** 上次抄表人，从未抄过为空 */
    private String lastReader;
    /** 上次抄表时间，从未抄过为空（前端据此显示「尚未抄过」） */
    private LocalDateTime lastReadingTime;
}
