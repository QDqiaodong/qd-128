package com.example.locker.repository;

import com.example.locker.entity.CollectionSuspensionRecord;
import com.example.locker.enums.CollectionSuspensionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CollectionSuspensionRecordRepository extends JpaRepository<CollectionSuspensionRecord, Long>, JpaSpecificationExecutor<CollectionSuspensionRecord> {

    boolean existsByRecordNo(String recordNo);

    boolean existsByLockerIdAndStatus(Long lockerId, CollectionSuspensionStatus status);

    List<CollectionSuspensionRecord> findByLockerIdOrderByCreateTimeDesc(Long lockerId);

    List<CollectionSuspensionRecord> findByLockerIdIn(Collection<Long> lockerIds);

    List<CollectionSuspensionRecord> findByLockerIdInAndStatus(Collection<Long> lockerIds, CollectionSuspensionStatus status);
}
