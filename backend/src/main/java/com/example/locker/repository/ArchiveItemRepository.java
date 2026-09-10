package com.example.locker.repository;

import com.example.locker.entity.ArchiveItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {

    List<ArchiveItem> findByArchiveId(Long archiveId);

    void deleteByArchiveId(Long archiveId);

    /**
     * 统计引用了指定柜体集合的归档快照数量（去重）。
     */
    long countDistinctArchiveIdByLockerIdIn(List<Long> lockerIds);
}
