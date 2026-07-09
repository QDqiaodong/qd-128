package com.example.locker.repository;

import com.example.locker.entity.Archive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchiveRepository extends JpaRepository<Archive, Long> {

    Page<Archive> findAllByOrderByCreateTimeDesc(Pageable pageable);

    List<Archive> findAllByOrderByCreateTimeDesc();
}
