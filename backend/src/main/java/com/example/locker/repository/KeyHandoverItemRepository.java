package com.example.locker.repository;

import com.example.locker.entity.KeyHandoverItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface KeyHandoverItemRepository extends JpaRepository<KeyHandoverItem, Long> {

    List<KeyHandoverItem> findByHandoverIdOrderByIdAsc(Long handoverId);

    List<KeyHandoverItem> findByRecordIdIn(Collection<Long> recordIds);

    List<KeyHandoverItem> findByLockerIdOrderByIdDesc(Long lockerId);

    /** 多条借用记录被点名次数，按记录 ID 分组返回 [recordId, cnt] */
    @Query("select i.recordId, count(i) from KeyHandoverItem i where i.recordId in :recordIds group by i.recordId")
    List<Object[]> countByRecordIds(@Param("recordIds") Collection<Long> recordIds);
}
