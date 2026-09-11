package com.example.locker.repository;

import com.example.locker.entity.InspectionReassignRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InspectionReassignRecordRepository extends JpaRepository<InspectionReassignRecord, Long> {

    List<InspectionReassignRecord> findByTaskIdOrderByReassignTimeDesc(Long taskId);

    List<InspectionReassignRecord> findByTaskIdIn(List<Long> taskIds);

    void deleteAllByTaskId(Long taskId);
}
