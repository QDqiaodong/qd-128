package com.example.locker.entity;

import com.example.locker.enums.CollectionSuspensionStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 夜间停收转投台账：夜间停止收件、快递转投时登记（停收中），
 * 记下开始停收时间、预计恢复时间和值班人；正在停收的柜必须能在本台账中查到。
 * 撕告示恢复后点「确认已恢复」，同一条记录状态变为已恢复，
 * 柜体列表/详情的「停收中」标记以本表为唯一数据源实时推导，随之恢复正常。
 */
@Entity
@Table(name = "collection_suspension_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionSuspensionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", unique = true, nullable = false, length = 40)
    private String recordNo;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /** 开始停收时间（默认当前时间，可补登过去时间） */
    @Column(name = "suspend_start_time", nullable = false)
    private LocalDateTime suspendStartTime;

    /** 预计恢复时间（必须晚于开始停收时间） */
    @Column(name = "expected_resume_time", nullable = false)
    private LocalDateTime expectedResumeTime;

    /** 值班人 */
    @Column(name = "duty_officer", nullable = false, length = 50)
    private String dutyOfficer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CollectionSuspensionStatus status = CollectionSuspensionStatus.SUSPENDED;

    /** 确认恢复人（留空默认系统管理员） */
    @Column(name = "resume_operator", length = 50)
    private String resumeOperator;

    /** 确认恢复时间 */
    @Column(name = "resume_time")
    private LocalDateTime resumeTime;

    /** 恢复说明 */
    @Column(name = "resume_note", length = 500)
    private String resumeNote;

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
