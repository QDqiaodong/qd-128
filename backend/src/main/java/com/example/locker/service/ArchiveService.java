package com.example.locker.service;

import com.example.locker.dto.ArchiveRequest;
import com.example.locker.dto.FilterRequest;
import com.example.locker.dto.LockerDTO;
import com.example.locker.entity.Archive;
import com.example.locker.entity.ArchiveItem;
import com.example.locker.entity.Locker;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.ArchiveItemRepository;
import com.example.locker.repository.ArchiveRepository;
import com.example.locker.repository.LockerRepository;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ArchiveService {

    @Autowired
    private ArchiveRepository archiveRepository;

    @Autowired
    private ArchiveItemRepository archiveItemRepository;

    @Autowired
    private LockerService lockerService;

    @Autowired
    private LockerRepository lockerRepository;

    @Transactional
    public Archive createArchive(ArchiveRequest request, List<Long> lockerIds) {
        Archive archive = new Archive();
        archive.setArchiveName(request.getArchiveName());
        archive.setFilterConditions(request.getFilterConditions());
        archive.setResultCount(lockerIds.size());
        archive.setOperator(request.getOperator());

        Archive savedArchive = archiveRepository.save(archive);

        for (Long lockerId : lockerIds) {
            ArchiveItem item = new ArchiveItem();
            item.setArchiveId(savedArchive.getId());
            item.setLockerId(lockerId);
            // 记录归档时刻的柜体状态快照
            lockerRepository.findById(lockerId).ifPresent(locker ->
                    item.setStatusSnapshot(locker.getStatus()));
            archiveItemRepository.save(item);
        }

        return savedArchive;
    }

    public Archive getArchiveById(Long id) {
        return archiveRepository.findById(id).orElseThrow(() -> 
                new RuntimeException("归档不存在: " + id));
    }

    public Page<Archive> getArchives(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return archiveRepository.findAllByOrderByCreateTimeDesc(pageable);
    }

    public List<Archive> getAllArchives() {
        return archiveRepository.findAllByOrderByCreateTimeDesc();
    }

    @Transactional
    public void deleteArchive(Long id) {
        archiveItemRepository.deleteByArchiveId(id);
        archiveRepository.deleteById(id);
    }

    /**
     * 返回归档快照中的柜体列表，状态以归档时的快照为准，
     * 即便柜体随后被停用/恢复，归档中仍可查看当时状态。
     */
    public List<LockerDTO> getArchiveLockers(Long archiveId) {
        List<ArchiveItem> items = archiveItemRepository.findByArchiveId(archiveId);

        List<LockerDTO> result = new ArrayList<>();
        for (ArchiveItem item : items) {
            Locker locker = lockerRepository.findById(item.getLockerId()).orElse(null);
            LockerDTO dto;
            if (locker != null) {
                dto = lockerService.convertToDTO(locker);
            } else {
                continue;
            }

            LockerStatus snapshot = item.getStatusSnapshot();
            if (snapshot != null) {
                dto.setSnapshotStatus(snapshot.name());
                dto.setSnapshotStatusName(snapshot.getDisplayName());
                dto.setFromSnapshot(true);
                // 当前实时状态冗余一份，便于归档详情与快照状态对比
                dto.setCurrentStatus(dto.getStatus());
                dto.setCurrentStatusName(dto.getStatusName());
            }
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public Archive createArchiveFromFilter(FilterRequest filterRequest, String archiveName, String operator) {
        List<LockerDTO> lockers = lockerService.filterLockersForArchive(filterRequest);
        List<Long> lockerIds = lockers.stream().map(LockerDTO::getId).collect(Collectors.toList());

        ArchiveRequest request = new ArchiveRequest();
        request.setArchiveName(archiveName);
        request.setFilterConditions(JSON.toJSONString(filterRequest));
        request.setResultCount(lockerIds.size());
        request.setOperator(operator);

        return createArchive(request, lockerIds);
    }

    public JSONObject getFilterConditions(Long archiveId) {
        Archive archive = getArchiveById(archiveId);
        if (archive.getFilterConditions() != null) {
            return JSON.parseObject(archive.getFilterConditions());
        }
        return new JSONObject();
    }
}
