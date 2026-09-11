package com.example.locker.repository;

import com.example.locker.entity.InspectionTask;
import com.example.locker.enums.InspectionTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface InspectionTaskRepository
        extends JpaRepository<InspectionTask, Long>, JpaSpecificationExecutor<InspectionTask> {

    Page<InspectionTask> findAllByOrderByCreateTimeDesc(Pageable pageable);

    long countByBuildingId(Long buildingId);

    long countByUnitId(Long unitId);
}
