package com.example.locker.entity;

import com.example.locker.enums.MeterReadingStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电表抄表单：物业按柜登记某自然月的电表读数、抄表人和抄表时间。
 * 账期（自然月）由抄表时间推导；同一柜同一自然月只允许存在一张未作废（有效）抄表单，
 * 作废必须填写作废原因，作废后的单据保留痕迹、不再占用该月额度。
 */
@Entity
@Table(name = "meter_reading_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeterReadingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", unique = true, nullable = false, length = 40)
    private String recordNo;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /** 账期（自然月，格式 yyyy-MM），由抄表时间推导，同一柜同一账期只允许一张有效单 */
    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    /** 电表读数（kWh） */
    @Column(name = "reading_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal readingValue;

    /** 抄表人 */
    @Column(name = "reader", nullable = false, length = 50)
    private String reader;

    /** 抄表时间（不能晚于当前时间，补登历史月份可选过去时间） */
    @Column(name = "reading_time", nullable = false)
    private LocalDateTime readingTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeterReadingStatus status = MeterReadingStatus.ACTIVE;

    /** 作废原因（作废必填） */
    @Column(name = "void_reason", length = 500)
    private String voidReason;

    /** 作废人 */
    @Column(name = "void_operator", length = 50)
    private String voidOperator;

    /** 作废时间 */
    @Column(name = "void_time")
    private LocalDateTime voidTime;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
