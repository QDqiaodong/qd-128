package com.example.locker.entity;

import com.example.locker.enums.ClearanceStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 滞留件清柜单：物业发现柜内超期未取件时按柜登记，
 * 记录滞留格口、件数、发现时间和处理人；办结时必须填写处理结果。
 */
@Entity
@Table(name = "clearance_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClearanceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", unique = true, nullable = false, length = 40)
    private String orderNo;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /** 滞留格口，如 A03,A07 */
    @Column(name = "overdue_compartments", nullable = false, length = 500)
    private String overdueCompartments;

    /** 滞留件数 */
    @Column(name = "package_count", nullable = false)
    private Integer packageCount;

    /** 发现时间（历史补登可为过去时间） */
    @Column(name = "found_time", nullable = false)
    private LocalDateTime foundTime;

    /** 处理人 */
    @Column(name = "handler", nullable = false, length = 50)
    private String handler;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClearanceStatus status = ClearanceStatus.PROCESSING;

    /** 处理结果（办结必填） */
    @Column(name = "handle_result", columnDefinition = "TEXT")
    private String handleResult;

    @Column(name = "complete_time")
    private LocalDateTime completeTime;

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
