package com.example.locker.repository;

import com.example.locker.entity.MeterReadingRecord;
import com.example.locker.enums.MeterReadingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeterReadingRecordRepository extends JpaRepository<MeterReadingRecord, Long>, JpaSpecificationExecutor<MeterReadingRecord> {

    boolean existsByRecordNo(String recordNo);

    boolean existsByLockerIdAndPeriodMonthAndStatus(Long lockerId, String periodMonth, MeterReadingStatus status);

    List<MeterReadingRecord> findByLockerIdOrderByCreateTimeDesc(Long lockerId);

    List<MeterReadingRecord> findByLockerIdInAndPeriodMonthAndStatus(Collection<Long> lockerIds, String periodMonth, MeterReadingStatus status);

    /** 指定账期之前的全部有效抄表单（账期 yyyy-MM 按字典序比较即可），用于批量推导各柜上次读数 */
    List<MeterReadingRecord> findByLockerIdInAndPeriodMonthLessThanAndStatus(Collection<Long> lockerIds, String periodMonth, MeterReadingStatus status);

    /** 指定账期之前最近的一张有效抄表单：登记校验「本月读数必须大于上次读数」的基准 */
    Optional<MeterReadingRecord> findFirstByLockerIdAndPeriodMonthLessThanAndStatusOrderByPeriodMonthDescIdDesc(
            Long lockerId, String periodMonth, MeterReadingStatus status);
}
