package com.example.locker.service;

import com.example.locker.dto.CollectionSuspensionCreateRequest;
import com.example.locker.dto.CollectionSuspensionRecordDTO;
import com.example.locker.dto.CollectionSuspensionResumeRequest;
import com.example.locker.dto.LockerCollectionSuspensionOverviewDTO;
import com.example.locker.dto.PageResponse;
import com.example.locker.entity.Building;
import com.example.locker.entity.CollectionSuspensionRecord;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.enums.CollectionSuspensionStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.CollectionSuspensionRecordRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class CollectionSuspensionService {

    @Autowired
    private CollectionSuspensionRecordRepository collectionSuspensionRecordRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    // ===================== 登记 =====================

    /**
     * 登记夜间停收转投。整个登记在单个事务内一次落库：
     * 校验不通过则整体回滚，不会留下只写了一半的台账。
     * 必填开始停收时间（默认当前）、预计恢复时间、值班人；同一柜已有停收中记录时不允许重复登记，
     * 先确认恢复后再登记。
     */
    @Transactional
    public CollectionSuspensionRecordDTO register(CollectionSuspensionCreateRequest request) {
        if (request == null || request.getLockerId() == null) {
            throw new IllegalArgumentException("请选择停收转投的柜体");
        }
        Locker locker = lockerRepository.findById(request.getLockerId()).orElseThrow(() ->
                new IllegalArgumentException("快递柜不存在: " + request.getLockerId()));

        LocalDateTime suspendStartTime = request.getSuspendStartTime() == null
                ? LocalDateTime.now() : request.getSuspendStartTime();
        if (suspendStartTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("开始停收时间不能晚于当前时间");
        }
        if (request.getExpectedResumeTime() == null) {
            throw new IllegalArgumentException("请填写预计恢复时间");
        }
        if (!request.getExpectedResumeTime().isAfter(suspendStartTime)) {
            throw new IllegalArgumentException("预计恢复时间必须晚于开始停收时间");
        }
        if (!StringUtils.hasText(request.getDutyOfficer())) {
            throw new IllegalArgumentException("请填写值班人");
        }
        if (collectionSuspensionRecordRepository.existsByLockerIdAndStatus(
                locker.getId(), CollectionSuspensionStatus.SUSPENDED)) {
            throw new IllegalArgumentException("该柜已有停收中的记录，请先确认已恢复后再登记");
        }

        CollectionSuspensionRecord record = new CollectionSuspensionRecord();
        record.setRecordNo(generateRecordNo());
        record.setLockerId(locker.getId());
        record.setSuspendStartTime(suspendStartTime);
        record.setExpectedResumeTime(request.getExpectedResumeTime());
        record.setDutyOfficer(request.getDutyOfficer().trim());
        record.setStatus(CollectionSuspensionStatus.SUSPENDED);
        record.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);

        return toDTO(collectionSuspensionRecordRepository.save(record));
    }

    // ===================== 确认已恢复 =====================

    /**
     * 撕告示恢复：同一条台账记录状态由停收中变为已恢复，
     * 记录确认恢复人与恢复时间；已恢复的记录不能重复恢复。
     * 恢复后柜体「停收中」标记实时恢复，台账按停收中/已恢复筛选找到的都是这同一条。
     */
    @Transactional
    public CollectionSuspensionRecordDTO resume(Long id, CollectionSuspensionResumeRequest request) {
        CollectionSuspensionRecord record = collectionSuspensionRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("停收记录不存在: " + id));
        if (record.getStatus() == CollectionSuspensionStatus.RESUMED) {
            throw new IllegalArgumentException("该记录已确认恢复，请勿重复操作");
        }

        record.setStatus(CollectionSuspensionStatus.RESUMED);
        record.setResumeTime(LocalDateTime.now());
        record.setResumeOperator(request != null && StringUtils.hasText(request.getResumeOperator())
                ? request.getResumeOperator().trim() : "系统管理员");
        record.setResumeNote(request != null && StringUtils.hasText(request.getResumeNote())
                ? request.getResumeNote().trim() : null);
        return toDTO(collectionSuspensionRecordRepository.save(record));
    }

    // ===================== 查询 =====================

    /**
     * 停收台账分页列表，可按状态（停收中/已恢复）、柜体、
     * 记录编号/值班人/柜体编号关键字过滤。已过预计恢复时间仍未恢复的记录带超时标记。
     */
    public PageResponse<CollectionSuspensionRecordDTO> getRecords(Integer page, Integer size, String status,
                                                                  Long lockerId, String keyword) {
        Specification<CollectionSuspensionRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"),
                        CollectionSuspensionStatus.fromCode(status.trim())));
            }
            if (lockerId != null) {
                predicates.add(cb.equal(root.get("lockerId"), lockerId));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Locker> lockerRoot = sub.from(Locker.class);
                sub.select(lockerRoot.get("id"))
                        .where(cb.like(lockerRoot.get("lockerNo"), like));
                predicates.add(cb.or(
                        cb.like(root.get("recordNo"), like),
                        cb.like(root.get("dutyOfficer"), like),
                        root.get("lockerId").in(sub)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<CollectionSuspensionRecord> recordPage = collectionSuspensionRecordRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<CollectionSuspensionRecordDTO> list = recordPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return new PageResponse<>(list, recordPage.getTotalElements(), page, size);
    }

    public CollectionSuspensionRecordDTO getRecord(Long id) {
        CollectionSuspensionRecord record = collectionSuspensionRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("停收记录不存在: " + id));
        return toDTO(record);
    }

    /**
     * 某台柜体的全部停收转投记录（柜详情页留存历史）。
     */
    public List<CollectionSuspensionRecordDTO> getLockerRecords(Long lockerId) {
        lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));
        return collectionSuspensionRecordRepository.findByLockerIdOrderByCreateTimeDesc(lockerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按停收状态一览：全部柜体（含停用柜）列出，存在停收中记录的柜体标记「停收中」并排前面。
     * 停收标记与停收中条数实时由停收台账推导，台账是唯一数据源，
     * 保证刷新后与柜体列表、详情的停收标记保持一致。
     */
    public List<LockerCollectionSuspensionOverviewDTO> getLockerOverview() {
        List<Locker> lockers = lockerRepository.findAll(Sort.by(Sort.Direction.ASC, "lockerNo"));
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Map<Long, List<CollectionSuspensionRecord>> byLocker = collectionSuspensionRecordRepository
                .findByLockerIdIn(lockerIds).stream()
                .collect(Collectors.groupingBy(CollectionSuspensionRecord::getLockerId));

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);
        LocalDateTime now = LocalDateTime.now();

        List<LockerCollectionSuspensionOverviewDTO> result = new ArrayList<>();
        for (Locker locker : lockers) {
            List<CollectionSuspensionRecord> records = byLocker.getOrDefault(locker.getId(), Collections.emptyList());
            List<CollectionSuspensionRecord> open = records.stream()
                    .filter(r -> r.getStatus() == CollectionSuspensionStatus.SUSPENDED)
                    .collect(Collectors.toList());

            LockerCollectionSuspensionOverviewDTO dto = new LockerCollectionSuspensionOverviewDTO();
            dto.setLockerId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setStatus(status.name());
            dto.setStatusName(status.getDisplayName());
            dto.setSuspended(!open.isEmpty());
            dto.setOpenRecordCount(open.size());
            dto.setOverdue(open.stream().anyMatch(r -> isOverdue(r, now)));
            dto.setTotalRecordCount(records.size());
            dto.setLastSuspendStartTime(records.stream()
                    .map(CollectionSuspensionRecord::getSuspendStartTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            dto.setExpectedResumeTime(open.stream()
                    .map(CollectionSuspensionRecord::getExpectedResumeTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            result.add(dto);
        }

        // 停收中的排前面，便于优先处理；其余按柜体编号排序
        result.sort(Comparator.comparing(LockerCollectionSuspensionOverviewDTO::getSuspended).reversed()
                .thenComparing(LockerCollectionSuspensionOverviewDTO::getLockerNo));
        return result;
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (CollectionSuspensionStatus status : CollectionSuspensionStatus.values()) {
            map.put(status.name(), status.getDisplayName());
        }
        return map;
    }

    // ===================== 转换与辅助 =====================

    /** 是否已过预计恢复时间仍未确认恢复：仅停收中记录才可能超时 */
    private boolean isOverdue(CollectionSuspensionRecord record, LocalDateTime now) {
        if (record.getStatus() != CollectionSuspensionStatus.SUSPENDED
                || record.getExpectedResumeTime() == null) {
            return false;
        }
        return !record.getExpectedResumeTime().isAfter(now);
    }

    private CollectionSuspensionRecordDTO toDTO(CollectionSuspensionRecord record) {
        CollectionSuspensionRecordDTO dto = new CollectionSuspensionRecordDTO();
        dto.setId(record.getId());
        dto.setRecordNo(record.getRecordNo());
        dto.setLockerId(record.getLockerId());
        dto.setSuspendStartTime(record.getSuspendStartTime());
        dto.setExpectedResumeTime(record.getExpectedResumeTime());
        dto.setDutyOfficer(record.getDutyOfficer());
        dto.setStatus(record.getStatus());
        dto.setStatusName(record.getStatus() == null ? null : record.getStatus().getDisplayName());
        // 停收中 = 告示未撕、尚未确认恢复，台账与柜体「停收中」标记统一据此推导
        dto.setSuspended(record.getStatus() == CollectionSuspensionStatus.SUSPENDED);
        LocalDateTime now = LocalDateTime.now();
        dto.setOverdue(isOverdue(record, now));
        LocalDateTime end = record.getStatus() == CollectionSuspensionStatus.RESUMED && record.getResumeTime() != null
                ? record.getResumeTime() : now;
        dto.setElapsedMinutes(record.getSuspendStartTime() == null ? null
                : Duration.between(record.getSuspendStartTime(), end).toMinutes());
        dto.setResumeOperator(record.getResumeOperator());
        dto.setResumeTime(record.getResumeTime());
        dto.setResumeNote(record.getResumeNote());
        dto.setRemark(record.getRemark());
        dto.setCreateTime(record.getCreateTime());
        dto.setUpdateTime(record.getUpdateTime());

        lockerRepository.findById(record.getLockerId()).ifPresent(locker -> {
            dto.setLockerNo(locker.getLockerNo());
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setLockerStatus(status.name());
            dto.setLockerStatusName(status.getDisplayName());
            buildingRepository.findById(locker.getBuildingId())
                    .ifPresent(b -> dto.setBuildingName(b.getName()));
            unitRepository.findById(locker.getUnitId())
                    .ifPresent(u -> dto.setUnitName(u.getName()));
        });
        return dto;
    }

    /** 台账编号：TS + 时间戳 + 随机后缀，冲突时重试 */
    private String generateRecordNo() {
        String recordNo;
        do {
            recordNo = "TS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        } while (collectionSuspensionRecordRepository.existsByRecordNo(recordNo));
        return recordNo;
    }

    private Map<Long, String> loadBuildingNames(Collection<Locker> lockers) {
        List<Long> ids = lockers.stream().map(Locker::getBuildingId).distinct().collect(Collectors.toList());
        return buildingRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Building::getId, Building::getName));
    }

    private Map<Long, String> loadUnitNames(Collection<Locker> lockers) {
        List<Long> ids = lockers.stream().map(Locker::getUnitId).distinct().collect(Collectors.toList());
        return unitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Unit::getId, Unit::getName));
    }
}
