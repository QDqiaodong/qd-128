package com.example.locker.repository;

import com.example.locker.entity.StatusChangeRecord;
import com.example.locker.enums.LockerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusChangeRecordRepository extends JpaRepository<StatusChangeRecord, Long> {

    List<StatusChangeRecord> findByLockerIdOrderByChangeTimeDesc(Long lockerId);

    List<StatusChangeRecord> findByLockerIdAndNewStatusOrderByChangeTimeDesc(Long lockerId, LockerStatus newStatus);
}
