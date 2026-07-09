package com.example.locker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "adjustment_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(name = "old_building_id")
    private Long oldBuildingId;

    @Column(name = "old_unit_id")
    private Long oldUnitId;

    @Column(name = "new_building_id", nullable = false)
    private Long newBuildingId;

    @Column(name = "new_unit_id", nullable = false)
    private Long newUnitId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "adjust_time")
    private LocalDateTime adjustTime;

    @PrePersist
    protected void onCreate() {
        adjustTime = LocalDateTime.now();
    }
}
