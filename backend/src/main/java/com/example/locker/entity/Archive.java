package com.example.locker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "archive")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Archive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_name", nullable = false, length = 200)
    private String archiveName;

    @Column(name = "filter_conditions", columnDefinition = "TEXT")
    private String filterConditions;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
