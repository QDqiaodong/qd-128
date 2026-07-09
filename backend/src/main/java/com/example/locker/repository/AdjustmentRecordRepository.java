package com.example.locker.repository;

import com.example.locker.entity.AdjustmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdjustmentRecordRepository extends JpaRepository<AdjustmentRecord, Long> {

    List<AdjustmentRecord> findByLockerIdOrderByAdjustTimeDesc(Long lockerId);
}
