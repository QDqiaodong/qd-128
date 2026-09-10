package com.example.locker.entity;

import com.example.locker.enums.LockerStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "status_change_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusChangeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 20)
    private LockerStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private LockerStatus newStatus;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "change_time")
    private LocalDateTime changeTime;

    @PrePersist
    protected void onCreate() {
        changeTime = LocalDateTime.now();
    }
}
