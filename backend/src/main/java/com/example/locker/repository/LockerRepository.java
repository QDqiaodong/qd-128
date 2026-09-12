package com.example.locker.repository;

import com.example.locker.entity.Locker;
import com.example.locker.enums.LockerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LockerRepository extends JpaRepository<Locker, Long>, JpaSpecificationExecutor<Locker> {

    Locker findByLockerNo(String lockerNo);

    boolean existsByLockerNo(String lockerNo);

    List<Locker> findByBuildingId(Long buildingId);

    List<Locker> findByStatus(LockerStatus status);

    List<Locker> findByBuildingIdAndStatus(Long buildingId, LockerStatus status);

    List<Locker> findByUnitId(Long unitId);

    List<Locker> findByUnitIdAndStatus(Long unitId, LockerStatus status);

    long countByBuildingId(Long buildingId);

    long countByUnitId(Long unitId);

    Page<Locker> findAll(Pageable pageable);
}
