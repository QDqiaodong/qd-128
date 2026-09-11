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

    /** 按编码查找单元（种子数据按编码幂等补种时使用；编码不保证全局唯一，返回列表） */
    List<Unit> findByCode(String code);

    boolean existsByBuildingIdAndCode(Long buildingId, String code);

    boolean existsByBuildingIdAndCodeAndIdNot(Long buildingId, String code, Long id);

    @Modifying
    @Transactional
    void deleteByBuildingId(Long buildingId);
}
