package com.example.locker.repository;

import com.example.locker.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByBuildingIdOrderBySortOrderAsc(Long buildingId);

    List<Unit> findByBuildingIdInOrderBySortOrderAsc(List<Long> buildingIds);
}
