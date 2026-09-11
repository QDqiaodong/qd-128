package com.example.locker.entity;

import com.example.locker.enums.CheckResult;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 巡检明细：任务中的每台快递柜一条记录。
 */
@Entity
@Table(name = "inspection_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_locker", columnNames = {"task_id", "locker_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "compartment_result", length = 20)
    private CheckResult compartmentResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_result", length = 20)
    private CheckResult screenResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_result", length = 20)
    private CheckResult lockResult;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "inspector", length = 50)
    private String inspector;

    @Column(name = "inspect_time")
    private LocalDateTime inspectTime;

    @PrePersist
    protected void onCreate() {
        inspectTime = LocalDateTime.now();
    }
}
