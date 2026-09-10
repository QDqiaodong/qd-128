package com.example.locker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import com.example.locker.enums.LockerStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "archive_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_id", nullable = false)
    private Long archiveId;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_snapshot", length = 20)
    private LockerStatus statusSnapshot;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
