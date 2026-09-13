package com.example.locker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 钥匙交接班点名明细：一条记录对应提交时点名的一条「借用中」借用台账，
 * 冗余柜体编号与借出人，便于交接后直接按柜查痕迹。交接不改变借用记录本身的状态。
 */
@Entity
@Table(name = "key_handover_item",
        indexes = {
                @Index(name = "idx_handover_item_handover", columnList = "handover_id"),
                @Index(name = "idx_handover_item_record", columnList = "record_id"),
                @Index(name = "idx_handover_item_locker", columnList = "locker_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyHandoverItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    /** 被点名的借用台账记录 */
    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /** 冗余柜体编号，交接后按柜查痕迹时直接展示 */
    @Column(name = "locker_no", length = 50)
    private String lockerNo;

    /** 点名时该单借出人快照 */
    @Column(name = "borrower_snapshot", length = 50)
    private String borrowerSnapshot;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
