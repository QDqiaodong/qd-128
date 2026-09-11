package com.example.locker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 巡检任务转派记录：未完成任务更换负责人时登记。
 * 仅记录负责人变更与原因，不改动任务状态及异常记录归属。
 */
@Entity
@Table(name = "inspection_reassign_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionReassignRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "old_assignee", length = 50)
    private String oldAssignee;

    @Column(name = "new_assignee", nullable = false, length = 50)
    private String newAssignee;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "reassign_time")
    private LocalDateTime reassignTime;

    @PrePersist
    protected void onCreate() {
        reassignTime = LocalDateTime.now();
    }
}
