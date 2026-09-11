package com.example.locker.repository;

import com.example.locker.entity.InspectionIssue;
import com.example.locker.enums.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InspectionIssueRepository extends JpaRepository<InspectionIssue, Long> {

    List<InspectionIssue> findByTaskIdOrderByCreateTimeDesc(Long taskId);

    List<InspectionIssue> findByTaskIdAndStatusOrderByCreateTimeDesc(Long taskId, IssueStatus status);

    List<InspectionIssue> findByTaskIdAndLockerIdOrderByCreateTimeDesc(Long taskId, Long lockerId);

    List<InspectionIssue> findByRecordId(Long recordId);

    long countByTaskIdAndStatus(Long taskId, IssueStatus status);
}
