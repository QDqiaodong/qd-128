package com.example.locker.repository;

import com.example.locker.entity.Locker;
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

    List<Locker> findByUnitId(Long unitId);

    Page<Locker> findAll(Pageable pageable);
}
