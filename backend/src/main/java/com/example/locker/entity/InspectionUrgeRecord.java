package com.example.locker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 巡检任务逾期催办记录：已逾期任务登记催办说明。
 * 催办仅追加台账，不改变任务状态与进度。
 */
@Entity
@Table(name = "inspection_urge_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionUrgeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "note", nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "urge_time")
    private LocalDateTime urgeTime;

    @PrePersist
    protected void onCreate() {
        urgeTime = LocalDateTime.now();
    }
}
