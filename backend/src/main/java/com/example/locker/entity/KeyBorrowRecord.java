package com.example.locker.entity;

import com.example.locker.enums.KeyBorrowStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 钥匙借用台账：物业临时取走某台快递柜柜门钥匙时登记，
 * 记录借出人、事由、借出时间和预计归还时间；归还时必须登记归还人和归还时间。
 * 同一柜钥匙存在未还记录时不允许再次借出。
 */
@Entity
@Table(name = "key_borrow_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyBorrowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", unique = true, nullable = false, length = 40)
    private String recordNo;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /** 借出人（取走钥匙的人） */
    @Column(name = "borrower", nullable = false, length = 50)
    private String borrower;

    /** 借用事由 */
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** 借出时间（历史补登可为过去时间） */
    @Column(name = "borrow_time", nullable = false)
    private LocalDateTime borrowTime;

    /** 预计归还时间 */
    @Column(name = "expected_return_time", nullable = false)
    private LocalDateTime expectedReturnTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KeyBorrowStatus status = KeyBorrowStatus.ON_LOAN;

    /** 归还人（归还必填） */
    @Column(name = "returner", length = 50)
    private String returner;

    /** 归还时间（归还必填） */
    @Column(name = "return_time")
    private LocalDateTime returnTime;

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
