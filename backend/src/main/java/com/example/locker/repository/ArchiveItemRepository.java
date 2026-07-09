package com.example.locker.repository;

import com.example.locker.entity.ArchiveItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {

    List<ArchiveItem> findByArchiveId(Long archiveId);

    void deleteByArchiveId(Long archiveId);
}
