package com.example.locker.repository;

import com.example.locker.entity.RepairTicket;
import com.example.locker.enums.RepairStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RepairTicketRepository extends JpaRepository<RepairTicket, Long>, JpaSpecificationExecutor<RepairTicket> {

    boolean existsByTicketNo(String ticketNo);

    boolean existsByLockerIdAndCompartmentNoAndStatus(Long lockerId, String compartmentNo, RepairStatus status);

    List<RepairTicket> findByLockerIdOrderByCreateTimeDesc(Long lockerId);

    List<RepairTicket> findByLockerIdIn(Collection<Long> lockerIds);

    List<RepairTicket> findByLockerIdInAndStatus(Collection<Long> lockerIds, RepairStatus status);

    long countByLockerIdAndStatus(Long lockerId, RepairStatus status);
}
