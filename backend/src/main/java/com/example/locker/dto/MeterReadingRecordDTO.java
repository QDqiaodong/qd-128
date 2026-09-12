package com.example.locker.dto;

import com.example.locker.enums.MeterReadingStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MeterReadingRecordDTO {

    private Long id;
    private String recordNo;
    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    /** 柜体当前生命周期状态（停用柜的历史抄表单仍可查看） */
    private String lockerStatus;
    private String lockerStatusName;
    /** 账期（自然月，格式 yyyy-MM），由抄表时间推导 */
    private String periodMonth;
    /** 电表读数（kWh） */
    private BigDecimal readingValue;
    /** 抄表人 */
    private String reader;
    /** 抄表时间 */
    private LocalDateTime readingTime;
    private MeterReadingStatus status;
    private String statusName;
    /** 有效（未作废）的抄表单置为 true，列表与详情据此标记 */
    private Boolean active;
    /** 是否当前自然月的抄表单，柜体页据此标记「本月已抄」 */
    private Boolean currentMonth;
    /** 作废原因（作废必填） */
    private String voidReason;
    /** 作废人 */
    private String voidOperator;
    /** 作废时间 */
    private LocalDateTime voidTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
