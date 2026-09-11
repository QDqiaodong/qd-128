package com.example.locker.repository;

import com.example.locker.entity.InspectionUrgeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InspectionUrgeRecordRepository extends JpaRepository<InspectionUrgeRecord, Long> {

    List<InspectionUrgeRecord> findByTaskIdOrderByUrgeTimeDesc(Long taskId);

    List<InspectionUrgeRecord> findByTaskIdIn(List<Long> taskIds);

    void deleteAllByTaskId(Long taskId);
}
