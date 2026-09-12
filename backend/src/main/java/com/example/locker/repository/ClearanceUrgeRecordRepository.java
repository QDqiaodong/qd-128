package com.example.locker.repository;

import com.example.locker.entity.ClearanceUrgeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClearanceUrgeRecordRepository extends JpaRepository<ClearanceUrgeRecord, Long> {

    List<ClearanceUrgeRecord> findByOrderIdOrderByUrgeTimeDescIdDesc(Long orderId);

    List<ClearanceUrgeRecord> findByOrderIdInOrderByUrgeTimeDescIdDesc(Collection<Long> orderIds);

    Optional<ClearanceUrgeRecord> findFirstByOrderIdAndStatusOrderByIdAsc(
            Long orderId, com.example.locker.enums.ClearanceUrgeStatus status);

    long countByOrderId(Long orderId);

    void deleteAllByOrderId(Long orderId);
}
