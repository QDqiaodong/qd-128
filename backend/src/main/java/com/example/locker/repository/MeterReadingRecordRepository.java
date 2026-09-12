package com.example.locker.repository;

import com.example.locker.entity.MeterReadingRecord;
import com.example.locker.enums.MeterReadingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MeterReadingRecordRepository extends JpaRepository<MeterReadingRecord, Long>, JpaSpecificationExecutor<MeterReadingRecord> {

    boolean existsByRecordNo(String recordNo);

    boolean existsByLockerIdAndPeriodMonthAndStatus(Long lockerId, String periodMonth, MeterReadingStatus status);

    List<MeterReadingRecord> findByLockerIdOrderByCreateTimeDesc(Long lockerId);

    List<MeterReadingRecord> findByLockerIdInAndPeriodMonthAndStatus(Collection<Long> lockerIds, String periodMonth, MeterReadingStatus status);
}
