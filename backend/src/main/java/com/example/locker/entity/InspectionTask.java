package com.example.locker.entity;

import com.example.locker.enums.InspectionCycle;
import com.example.locker.enums.InspectionTaskStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inspection_task")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 巡检范围：楼栋 */
    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** 巡检范围：单元，可为空表示整栋楼 */
    @Column(name = "unit_id")
    private Long unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle", nullable = false, length = 20)
    private InspectionCycle cycle;

    @Column(name = "assignee", length = 50)
    private String assignee;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InspectionTaskStatus status = InspectionTaskStatus.PENDING;

    @Column(name = "total_lockers", nullable = false)
    private Integer totalLockers = 0;

    @Column(name = "completed_lockers", nullable = false)
    private Integer completedLockers = 0;

    @Column(name = "abnormal_count", nullable = false)
    private Integer abnormalCount = 0;

    @Column(name = "pending_issue_count", nullable = false)
    private Integer pendingIssueCount = 0;

    @Column(name = "creator", length = 50)
    private String creator;

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
