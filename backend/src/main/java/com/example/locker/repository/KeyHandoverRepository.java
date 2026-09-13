package com.example.locker.repository;

import com.example.locker.entity.KeyHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KeyHandoverRepository extends JpaRepository<KeyHandover, Long> {

    boolean existsByHandoverNo(String handoverNo);
}
