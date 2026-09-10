package com.example.locker.repository;

import com.example.locker.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByBuildingIdOrderBySortOrderAsc(Long buildingId);

    List<Unit> findByBuildingIdInOrderBySortOrderAsc(List<Long> buildingIds);

    boolean existsByBuildingIdAndCode(Long buildingId, String code);

    boolean existsByBuildingIdAndCodeAndIdNot(Long buildingId, String code, Long id);

    @Modifying
    @Transactional
    void deleteByBuildingId(Long buildingId);
}
