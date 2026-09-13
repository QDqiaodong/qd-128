package com.example.locker.repository;

import com.example.locker.entity.KeyBorrowRecord;
import com.example.locker.enums.KeyBorrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface KeyBorrowRecordRepository extends JpaRepository<KeyBorrowRecord, Long>, JpaSpecificationExecutor<KeyBorrowRecord> {

    boolean existsByRecordNo(String recordNo);

    boolean existsByLockerIdAndStatus(Long lockerId, KeyBorrowStatus status);

    List<KeyBorrowRecord> findByLockerIdOrderByCreateTimeDesc(Long lockerId);

    List<KeyBorrowRecord> findByLockerIdIn(Collection<Long> lockerIds);

    List<KeyBorrowRecord> findByLockerIdInAndStatus(Collection<Long> lockerIds, KeyBorrowStatus status);

    List<KeyBorrowRecord> findByStatusOrderByCreateTimeDesc(KeyBorrowStatus status);

    long countByLockerIdAndStatus(Long lockerId, KeyBorrowStatus status);
}
