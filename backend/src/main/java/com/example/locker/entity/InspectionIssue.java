package com.example.locker.entity;

import com.example.locker.enums.IssueStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 巡检异常待处理记录：检查项被标记为异常时生成。
 */
@Entity
@Table(name = "inspection_issue")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /** 检查项 code：compartment/screen/lock */
    @Column(name = "check_item", nullable = false, length = 20)
    private String checkItem;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IssueStatus status = IssueStatus.PENDING;

    @Column(name = "handler", length = 50)
    private String handler;

    @Column(name = "handle_note", columnDefinition = "TEXT")
    private String handleNote;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "handle_time")
    private LocalDateTime handleTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
