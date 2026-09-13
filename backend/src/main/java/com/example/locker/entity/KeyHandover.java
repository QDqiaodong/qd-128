package com.example.locker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 钥匙交接班：物业交接班时，交班人在钥匙借用台账里对全部当前未还柜逐一点名后提交，
 * 写明接班人和交接说明。交接只转移保管责任、留存交接痕迹，不改变借用状态：
 * 被点名的借用记录仍是「借用中」，按柜一览的未还条数不变。
 * 一次交接必须勾齐当时全部未还柜，否则整体回滚，不会留下半次交接。
 */
@Entity
@Table(name = "key_handover")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_no", unique = true, nullable = false, length = 40)
    private String handoverNo;

    /** 交班人（提交交接的人，必填） */
    @Column(name = "handover_from", nullable = false, length = 50)
    private String handoverFrom;

    /** 接班人（必填） */
    @Column(name = "handover_to", nullable = false, length = 50)
    private String handoverTo;

    /** 交接说明（必填） */
    @Column(name = "handover_note", nullable = false, length = 500)
    private String handoverNote;

    /** 提交时点名的未还柜数量（应等于当时全部借用中记录条数） */
    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

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
