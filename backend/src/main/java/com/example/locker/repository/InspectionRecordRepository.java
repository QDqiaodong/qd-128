package com.example.locker.repository;

import com.example.locker.entity.InspectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InspectionRecordRepository extends JpaRepository<InspectionRecord, Long> {

    List<InspectionRecord> findByTaskIdOrderByIdAsc(Long taskId);

    Optional<InspectionRecord> findByTaskIdAndLockerId(Long taskId, Long lockerId);

    long countByTaskId(Long taskId);
}
