package com.example.locker.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 登记电表抄表请求
 */
@Data
public class MeterReadingCreateRequest {

    private Long lockerId;
    /** 电表读数（kWh），不能为空且不能为负 */
    private BigDecimal readingValue;
    /** 抄表人 */
    private String reader;
    /** 抄表时间（不能晚于当前时间；账期自然月由该时间推导，补登历史月份可选过去时间） */
    private LocalDateTime readingTime;
    private String remark;
}
