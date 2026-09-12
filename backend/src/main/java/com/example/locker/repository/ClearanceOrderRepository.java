package com.example.locker.repository;

import com.example.locker.entity.ClearanceOrder;
import com.example.locker.enums.ClearanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClearanceOrderRepository extends JpaRepository<ClearanceOrder, Long>, JpaSpecificationExecutor<ClearanceOrder> {

    boolean existsByOrderNo(String orderNo);

    /** 行级锁加载清柜单，串行化同一单的催领登记，避免并发挂出两笔未关闭催领 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ClearanceOrder o where o.id = :id")
    Optional<ClearanceOrder> findByIdForUpdate(@Param("id") Long id);

    List<ClearanceOrder> findByLockerIdOrderByCreateTimeDesc(Long lockerId);

    List<ClearanceOrder> findByLockerIdIn(Collection<Long> lockerIds);

    List<ClearanceOrder> findByLockerIdInAndStatus(Collection<Long> lockerIds, ClearanceStatus status);

    long countByLockerIdAndStatus(Long lockerId, ClearanceStatus status);
}
