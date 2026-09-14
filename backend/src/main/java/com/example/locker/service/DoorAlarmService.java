package com.example.locker.service;

import com.example.locker.dto.DoorAlarmCloseRequest;
import com.example.locker.dto.DoorAlarmCreateRequest;
import com.example.locker.dto.DoorAlarmRecordDTO;
import com.example.locker.dto.LockerDoorAlarmOverviewDTO;
import com.example.locker.dto.PageResponse;
import com.example.locker.entity.Building;
import com.example.locker.entity.DoorAlarmRecord;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.enums.DoorAlarmStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.DoorAlarmRecordRepository;
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
public class DoorAlarmService {

    /** 约定关严分钟数默认值：登记时未指定则按系统约定 10 分钟 */
    public static final int DEFAULT_THRESHOLD_MINUTES = 10;

    @Autowired
    private DoorAlarmRecordRepository doorAlarmRecordRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    // ===================== 登记 =====================

    /**
     * 登记柜门未关告警。整个登记在单个事务内一次落库：
     * 校验不通过则整体回滚，不会留下只写了一半的台账。
     * 同一柜已有未处理告警时不允许重复登记，先确认关闭后再登记。
     */
    @Transactional
    public DoorAlarmRecordDTO reportAlarm(DoorAlarmCreateRequest request) {
        if (request == null || request.getLockerId() == null) {
            throw new IllegalArgumentException("请选择柜门未关的柜体");
        }
        Locker locker = lockerRepository.findById(request.getLockerId()).orElseThrow(() ->
                new IllegalArgumentException("快递柜不存在: " + request.getLockerId()));

        if (!StringUtils.hasText(request.getReporter())) {
            throw new IllegalArgumentException("请填写上报人");
        }
        LocalDateTime doorOpenTime = request.getDoorOpenTime() == null
                ? LocalDateTime.now() : request.getDoorOpenTime();
        if (doorOpenTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("发现未关时间不能晚于当前时间");
        }
        Integer thresholdMinutes = request.getThresholdMinutes();
        if (thresholdMinutes == null) {
            thresholdMinutes = DEFAULT_THRESHOLD_MINUTES;
        }
        if (thresholdMinutes < 1) {
            throw new IllegalArgumentException("约定关严分钟数必须大于 0");
        }
        if (doorAlarmRecordRepository.existsByLockerIdAndStatus(locker.getId(), DoorAlarmStatus.OPEN)) {
            throw new IllegalArgumentException("该柜已有未处理的柜门未关告警，请先确认关闭后再登记");
        }

        DoorAlarmRecord record = new DoorAlarmRecord();
        record.setAlarmNo(generateAlarmNo());
        record.setLockerId(locker.getId());
        record.setDoorOpenTime(doorOpenTime);
        record.setThresholdMinutes(thresholdMinutes);
        record.setReporter(request.getReporter().trim());
        record.setStatus(DoorAlarmStatus.OPEN);
        record.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);

        return toDTO(doorAlarmRecordRepository.save(record));
    }

    // ===================== 确认已关闭 =====================

    /**
     * 确认柜门已关严：同一条台账记录状态由未处理变为已关闭，
     * 记录确认关闭人与关闭时间；已关闭的记录不能重复关闭。
     * 关闭后柜体「柜门未关」标记实时恢复，台账按未处理/已关闭筛选找到的都是这同一条。
     */
    @Transactional
    public DoorAlarmRecordDTO closeAlarm(Long id, DoorAlarmCloseRequest request) {
        DoorAlarmRecord record = doorAlarmRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("告警记录不存在: " + id));
        if (record.getStatus() == DoorAlarmStatus.CLOSED) {
            throw new IllegalArgumentException("该告警已确认关闭，请勿重复操作");
        }

        record.setStatus(DoorAlarmStatus.CLOSED);
        record.setCloseTime(LocalDateTime.now());
        record.setCloseOperator(request != null && StringUtils.hasText(request.getCloseOperator())
                ? request.getCloseOperator().trim() : "系统管理员");
        record.setCloseNote(request != null && StringUtils.hasText(request.getCloseNote())
                ? request.getCloseNote().trim() : null);
        return toDTO(doorAlarmRecordRepository.save(record));
    }

    // ===================== 查询 =====================

    /**
     * 柜门未关告警台账分页列表，可按状态（未处理/已关闭）、柜体、
     * 告警编号/上报人/柜体编号关键字过滤。超过约定分钟仍未关严的记录带超时标记。
     */
    public PageResponse<DoorAlarmRecordDTO> getAlarms(Integer page, Integer size, String status,
                                                      Long lockerId, String keyword) {
        Specification<DoorAlarmRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), DoorAlarmStatus.fromCode(status.trim())));
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
                        cb.like(root.get("alarmNo"), like),
                        cb.like(root.get("reporter"), like),
                        root.get("lockerId").in(sub)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DoorAlarmRecord> alarmPage = doorAlarmRecordRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<DoorAlarmRecordDTO> list = alarmPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return new PageResponse<>(list, alarmPage.getTotalElements(), page, size);
    }

    public DoorAlarmRecordDTO getAlarm(Long id) {
        DoorAlarmRecord record = doorAlarmRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("告警记录不存在: " + id));
        return toDTO(record);
    }

    /**
     * 某台柜体的全部柜门未关告警（柜详情页留存历史）。
     */
    public List<DoorAlarmRecordDTO> getLockerAlarms(Long lockerId) {
        lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));
        return doorAlarmRecordRepository.findByLockerIdOrderByCreateTimeDesc(lockerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按柜门状态一览：全部柜体（含停用柜）列出，存在未处理告警的柜体标记「柜门未关」并排前面。
     * 未关标记与未处理条数实时由告警台账推导，台账是唯一数据源，
     * 保证刷新后与柜体列表、详情的未关标记保持一致。
     */
    public List<LockerDoorAlarmOverviewDTO> getLockerOverview() {
        List<Locker> lockers = lockerRepository.findAll(Sort.by(Sort.Direction.ASC, "lockerNo"));
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Map<Long, List<DoorAlarmRecord>> byLocker = doorAlarmRecordRepository.findByLockerIdIn(lockerIds)
                .stream().collect(Collectors.groupingBy(DoorAlarmRecord::getLockerId));

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);
        LocalDateTime now = LocalDateTime.now();

        List<LockerDoorAlarmOverviewDTO> result = new ArrayList<>();
        for (Locker locker : lockers) {
            List<DoorAlarmRecord> alarms = byLocker.getOrDefault(locker.getId(), Collections.emptyList());
            List<DoorAlarmRecord> open = alarms.stream()
                    .filter(a -> a.getStatus() == DoorAlarmStatus.OPEN)
                    .collect(Collectors.toList());

            LockerDoorAlarmOverviewDTO dto = new LockerDoorAlarmOverviewDTO();
            dto.setLockerId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setStatus(status.name());
            dto.setStatusName(status.getDisplayName());
            dto.setDoorAjar(!open.isEmpty());
            dto.setOpenAlarmCount(open.size());
            dto.setOvertime(open.stream().anyMatch(a -> isOvertime(a, now)));
            dto.setTotalAlarmCount(alarms.size());
            dto.setLastDoorOpenTime(alarms.stream()
                    .map(DoorAlarmRecord::getDoorOpenTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            result.add(dto);
        }

        // 柜门未关的排前面，便于优先处理；其余按柜体编号排序
        result.sort(Comparator.comparing(LockerDoorAlarmOverviewDTO::getDoorAjar).reversed()
                .thenComparing(LockerDoorAlarmOverviewDTO::getLockerNo));
        return result;
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (DoorAlarmStatus status : DoorAlarmStatus.values()) {
            map.put(status.name(), status.getDisplayName());
        }
        return map;
    }

    // ===================== 转换与辅助 =====================

    /** 是否已超约定分钟仍未关严：仅未处理记录才可能超时 */
    private boolean isOvertime(DoorAlarmRecord record, LocalDateTime now) {
        if (record.getStatus() != DoorAlarmStatus.OPEN || record.getDoorOpenTime() == null) {
            return false;
        }
        int threshold = record.getThresholdMinutes() == null
                ? DEFAULT_THRESHOLD_MINUTES : record.getThresholdMinutes();
        return !record.getDoorOpenTime().plusMinutes(threshold).isAfter(now);
    }

    private DoorAlarmRecordDTO toDTO(DoorAlarmRecord record) {
        DoorAlarmRecordDTO dto = new DoorAlarmRecordDTO();
        dto.setId(record.getId());
        dto.setAlarmNo(record.getAlarmNo());
        dto.setLockerId(record.getLockerId());
        dto.setDoorOpenTime(record.getDoorOpenTime());
        dto.setThresholdMinutes(record.getThresholdMinutes());
        dto.setReporter(record.getReporter());
        dto.setStatus(record.getStatus());
        dto.setStatusName(record.getStatus() == null ? null : record.getStatus().getDisplayName());
        // 未处理 = 柜门仍未确认关严，台账与柜体「柜门未关」标记统一据此推导
        dto.setOpen(record.getStatus() == DoorAlarmStatus.OPEN);
        LocalDateTime now = LocalDateTime.now();
        dto.setOvertime(isOvertime(record, now));
        LocalDateTime end = record.getStatus() == DoorAlarmStatus.CLOSED && record.getCloseTime() != null
                ? record.getCloseTime() : now;
        dto.setElapsedMinutes(record.getDoorOpenTime() == null ? null
                : Duration.between(record.getDoorOpenTime(), end).toMinutes());
        dto.setCloseOperator(record.getCloseOperator());
        dto.setCloseTime(record.getCloseTime());
        dto.setCloseNote(record.getCloseNote());
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

    /** 告警编号：MJ + 时间戳 + 随机后缀，冲突时重试 */
    private String generateAlarmNo() {
        String alarmNo;
        do {
            alarmNo = "MJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        } while (doorAlarmRecordRepository.existsByAlarmNo(alarmNo));
        return alarmNo;
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
