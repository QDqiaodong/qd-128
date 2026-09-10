package com.example.locker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import com.example.locker.enums.LockerStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "locker")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Locker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_no", unique = true, nullable = false, length = 50)
    private String lockerNo;

    @Column(name = "compartment_count", nullable = false)
    private Integer compartmentCount;

    @Column(name = "spec_type", nullable = false, length = 20)
    private String specType;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "floor", length = 20)
    private String floor;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LockerStatus status = LockerStatus.ACTIVE;

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
