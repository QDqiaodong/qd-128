package com.example.locker.entity;

import com.example.locker.enums.RepairStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 格口报修台账：物业在柜详情登记故障格口、故障现象和报修人后建单（处理中）；
 * 完工时填写处理人和处理结果，状态变为已修好。
 * 柜详情报修条数与柜体可用标记均以本表为唯一数据源实时推导。
 */
@Entity
@Table(name = "repair_ticket")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepairTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_no", unique = true, nullable = false, length = 40)
    private String ticketNo;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /** 故障格口编号 */
    @Column(name = "compartment_no", nullable = false, length = 20)
    private String compartmentNo;

    /** 故障现象 */
    @Column(name = "symptom", nullable = false, length = 500)
    private String symptom;

    /** 报修人 */
    @Column(name = "reporter", nullable = false, length = 50)
    private String reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RepairStatus status = RepairStatus.PROCESSING;

    /** 处理人（完工必填） */
    @Column(name = "handler", length = 50)
    private String handler;

    /** 处理结果（完工必填） */
    @Column(name = "repair_result", length = 500)
    private String repairResult;

    /** 完工时间 */
    @Column(name = "fixed_time")
    private LocalDateTime fixedTime;

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
