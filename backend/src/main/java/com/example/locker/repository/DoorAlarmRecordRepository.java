package com.example.locker.repository;

import com.example.locker.entity.DoorAlarmRecord;
import com.example.locker.enums.DoorAlarmStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DoorAlarmRecordRepository extends JpaRepository<DoorAlarmRecord, Long>, JpaSpecificationExecutor<DoorAlarmRecord> {

    boolean existsByAlarmNo(String alarmNo);

    boolean existsByLockerIdAndStatus(Long lockerId, DoorAlarmStatus status);

    List<DoorAlarmRecord> findByLockerIdOrderByCreateTimeDesc(Long lockerId);

    List<DoorAlarmRecord> findByLockerIdIn(Collection<Long> lockerIds);

    List<DoorAlarmRecord> findByLockerIdInAndStatus(Collection<Long> lockerIds, DoorAlarmStatus status);
}
