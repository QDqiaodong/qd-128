package com.example.locker.repository;

import com.example.locker.entity.ClearanceOrder;
import com.example.locker.enums.ClearanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ClearanceOrderRepository extends JpaRepository<ClearanceOrder, Long>, JpaSpecificationExecutor<ClearanceOrder> {

    boolean existsByOrderNo(String orderNo);

    List<ClearanceOrder> findByLockerIdOrderByCreateTimeDesc(Long lockerId);

    List<ClearanceOrder> findByLockerIdIn(Collection<Long> lockerIds);

    List<ClearanceOrder> findByLockerIdInAndStatus(Collection<Long> lockerIds, ClearanceStatus status);

    long countByLockerIdAndStatus(Long lockerId, ClearanceStatus status);
}
