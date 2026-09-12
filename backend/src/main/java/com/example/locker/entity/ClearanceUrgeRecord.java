package com.example.locker.entity;

import com.example.locker.enums.ClearanceUrgeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 清柜单当面催领记录：办理中的清柜单可逐笔登记当面催领，
 * 记录催领时间与经办人。同一张办理中的单同时只能挂一笔未关闭催领，
 * 关闭后才能再记下一笔；清柜单办结时未关闭的催领在同一事务内自动关闭。
 */
@Entity
@Table(name = "clearance_urge_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClearanceUrgeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 催领时间（当面催领发生时间，可补登过去时间） */
    @Column(name = "urge_time", nullable = false)
    private LocalDateTime urgeTime;

    /** 经办人（当面催领人） */
    @Column(name = "operator", nullable = false, length = 50)
    private String operator;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClearanceUrgeStatus status = ClearanceUrgeStatus.OPEN;

    /** 关闭说明（选填） */
    @Column(name = "close_note", columnDefinition = "TEXT")
    private String closeNote;

    /** 关闭经办人 */
    @Column(name = "close_operator", length = 50)
    private String closeOperator;

    @Column(name = "close_time")
    private LocalDateTime closeTime;

    /** 办结自动关闭标记：1-办结时系统自动关闭，0-人工关闭 */
    @Column(name = "auto_closed", nullable = false)
    private Boolean autoClosed = false;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
