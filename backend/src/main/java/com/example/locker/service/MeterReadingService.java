package com.example.locker.service;

import com.example.locker.dto.*;
import com.example.locker.entity.Building;
import com.example.locker.entity.Locker;
import com.example.locker.entity.MeterReadingRecord;
import com.example.locker.entity.Unit;
import com.example.locker.enums.LockerStatus;
import com.example.locker.enums.MeterReadingStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.MeterReadingRecordRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class MeterReadingService {

    @Autowired
    private MeterReadingRecordRepository meterReadingRecordRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    // ===================== 登记抄表 =====================

    /**
     * 登记电表抄表。整个登记在单个事务内一次落库：
     * 全部校验通过后才保存，校验不通过则整体回滚，不会写出半张抄表单。
     * 账期（自然月）由抄表时间推导；同一柜同一自然月只允许一张未作废的有效单，
     * 该月已存在有效单时直接拦截（已作废的单不占用额度，作废后可重新登记）。
     */
    @Transactional
    public MeterReadingRecordDTO createRecord(MeterReadingCreateRequest request) {
        if (request == null || request.getLockerId() == null) {
            throw new IllegalArgumentException("请选择抄表的柜体");
        }
        Locker locker = lockerRepository.findById(request.getLockerId()).orElseThrow(() ->
                new IllegalArgumentException("快递柜不存在: " + request.getLockerId()));

        if (request.getReadingValue() == null) {
            throw new IllegalArgumentException("请填写电表读数");
        }
        if (request.getReadingValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("电表读数不能为负数");
        }
        if (!StringUtils.hasText(request.getReader())) {
            throw new IllegalArgumentException("请填写抄表人");
        }
        if (request.getReadingTime() == null) {
            throw new IllegalArgumentException("请选择抄表时间");
        }
        if (request.getReadingTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("抄表时间不能晚于当前时间");
        }

        String periodMonth = toPeriodMonth(request.getReadingTime());
        if (meterReadingRecordRepository.existsByLockerIdAndPeriodMonthAndStatus(
                locker.getId(), periodMonth, MeterReadingStatus.ACTIVE)) {
            throw new IllegalArgumentException("该柜 " + periodMonth + " 已存在有效抄表单，同一柜同一自然月不能挂两张未作废单；如需更正请先作废原单");
        }

        MeterReadingRecord record = new MeterReadingRecord();
        record.setRecordNo(generateRecordNo());
        record.setLockerId(locker.getId());
        record.setPeriodMonth(periodMonth);
        record.setReadingValue(request.getReadingValue());
        record.setReader(request.getReader().trim());
        record.setReadingTime(request.getReadingTime());
        record.setStatus(MeterReadingStatus.ACTIVE);
        record.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);

        return toDTO(meterReadingRecordRepository.save(record));
    }

    // ===================== 作废抄表单 =====================

    /**
     * 作废抄表单：必须填写作废原因；已作废的单不能重复作废。
     * 作废只更新单据状态与作废痕迹，在单个事务内一次落库；
     * 作废后该柜该月不再占用有效单额度，可重新登记。
     */
    @Transactional
    public MeterReadingRecordDTO voidRecord(Long id, MeterReadingVoidRequest request) {
        if (request == null || !StringUtils.hasText(request.getVoidReason())) {
            throw new IllegalArgumentException("作废必须填写作废原因");
        }
        MeterReadingRecord record = meterReadingRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("抄表单不存在: " + id));
        if (record.getStatus() == MeterReadingStatus.VOIDED) {
            throw new IllegalArgumentException("该抄表单已作废，请勿重复操作");
        }

        record.setStatus(MeterReadingStatus.VOIDED);
        record.setVoidReason(request.getVoidReason().trim());
        record.setVoidOperator(StringUtils.hasText(request.getVoidOperator())
                ? request.getVoidOperator().trim() : "系统管理员");
        record.setVoidTime(LocalDateTime.now());
        return toDTO(meterReadingRecordRepository.save(record));
    }

    // ===================== 查询 =====================

    /**
     * 抄表单分页列表，可按账期、单据状态（有效/已作废）、柜体、单号/抄表人/柜体编号关键字过滤。
     * 全部条件实时查询抄表单表，刷新后筛选结果与柜体页、本月已抄台数保持一致。
     */
    public PageResponse<MeterReadingRecordDTO> getRecords(Integer page, Integer size, String periodMonth,
                                                          String status, Long lockerId, String keyword) {
        Specification<MeterReadingRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(periodMonth)) {
                predicates.add(cb.equal(root.get("periodMonth"), periodMonth.trim()));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), MeterReadingStatus.fromCode(status.trim())));
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
                        cb.like(root.get("reader"), like),
                        root.get("lockerId").in(sub)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<MeterReadingRecord> recordPage = meterReadingRecordRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<MeterReadingRecordDTO> list = recordPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return new PageResponse<>(list, recordPage.getTotalElements(), page, size);
    }

    public MeterReadingRecordDTO getRecord(Long id) {
        MeterReadingRecord record = meterReadingRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("抄表单不存在: " + id));
        return toDTO(record);
    }

    /**
     * 某台柜体的全部抄表单（柜体详情页留存历史，含已作废单）。
     */
    public List<MeterReadingRecordDTO> getLockerRecords(Long lockerId) {
        lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));
        return meterReadingRecordRepository.findByLockerIdOrderByCreateTimeDesc(lockerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按柜本月抄表状态一览：全部柜体（含停用柜）列出，未抄的排前面。
     * 已抄/未抄标记与本月读数实时由有效抄表单推导，抄表单是唯一数据源，
     * 保证刷新后一览、本月已抄台数与柜体页读数保持一致。
     */
    public List<LockerMeterReadingOverviewDTO> getLockerOverview(String periodMonth) {
        String period = StringUtils.hasText(periodMonth) ? periodMonth.trim() : currentPeriod();
        List<Locker> lockers = lockerRepository.findAll(Sort.by(Sort.Direction.ASC, "lockerNo"));
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Map<Long, MeterReadingRecord> activeByLocker = meterReadingRecordRepository
                .findByLockerIdInAndPeriodMonthAndStatus(lockerIds, period, MeterReadingStatus.ACTIVE)
                .stream().collect(Collectors.toMap(MeterReadingRecord::getLockerId, r -> r, (a, b) -> a));

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);

        List<LockerMeterReadingOverviewDTO> result = new ArrayList<>();
        for (Locker locker : lockers) {
            MeterReadingRecord active = activeByLocker.get(locker.getId());

            LockerMeterReadingOverviewDTO dto = new LockerMeterReadingOverviewDTO();
            dto.setLockerId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setStatus(status.name());
            dto.setStatusName(status.getDisplayName());
            dto.setPeriodMonth(period);
            dto.setRead(active != null);
            if (active != null) {
                dto.setRecordId(active.getId());
                dto.setReadingValue(active.getReadingValue());
                dto.setReader(active.getReader());
                dto.setReadingTime(active.getReadingTime());
            }
            result.add(dto);
        }

        // 未抄的排前面，便于优先跟进补抄；其余按柜体编号排序
        result.sort(Comparator.comparing(LockerMeterReadingOverviewDTO::getRead)
                .thenComparing(LockerMeterReadingOverviewDTO::getLockerNo));
        return result;
    }

    /**
     * 登记抄表的可选柜体：包含全部生命周期状态；
     * 本月已存在有效抄表单的柜体标记出来，前端置灰，后端登记时也会再次拦截。
     */
    public List<MeterReadingLockerOptionDTO> getLockerOptions() {
        List<Locker> lockers = lockerRepository.findAll(Sort.by(Sort.Direction.ASC, "lockerNo"));
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Set<Long> readLockerIds = meterReadingRecordRepository
                .findByLockerIdInAndPeriodMonthAndStatus(lockerIds, currentPeriod(), MeterReadingStatus.ACTIVE)
                .stream().map(MeterReadingRecord::getLockerId).collect(Collectors.toSet());

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);

        return lockers.stream().map(locker -> {
            MeterReadingLockerOptionDTO dto = new MeterReadingLockerOptionDTO();
            dto.setId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setStatus(status.name());
            dto.setStatusName(status.getDisplayName());
            dto.setReadThisMonth(readLockerIds.contains(locker.getId()));
            return dto;
        }).collect(Collectors.toList());
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (MeterReadingStatus status : MeterReadingStatus.values()) {
            map.put(status.name(), status.getDisplayName());
        }
        return map;
    }

    // ===================== 转换与辅助 =====================

    private MeterReadingRecordDTO toDTO(MeterReadingRecord record) {
        MeterReadingRecordDTO dto = new MeterReadingRecordDTO();
        dto.setId(record.getId());
        dto.setRecordNo(record.getRecordNo());
        dto.setLockerId(record.getLockerId());
        dto.setPeriodMonth(record.getPeriodMonth());
        dto.setReadingValue(record.getReadingValue());
        dto.setReader(record.getReader());
        dto.setReadingTime(record.getReadingTime());
        dto.setStatus(record.getStatus());
        dto.setStatusName(record.getStatus() == null ? null : record.getStatus().getDisplayName());
        // 有效 = 未作废，列表与详情统一据此标记
        dto.setActive(record.getStatus() == MeterReadingStatus.ACTIVE);
        // 当前自然月的有效单 → 柜体页「本月已抄」与本月已抄台数的统计口径
        dto.setCurrentMonth(record.getStatus() == MeterReadingStatus.ACTIVE
                && Objects.equals(record.getPeriodMonth(), currentPeriod()));
        dto.setVoidReason(record.getVoidReason());
        dto.setVoidOperator(record.getVoidOperator());
        dto.setVoidTime(record.getVoidTime());
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

    /** 当前账期（自然月，格式 yyyy-MM） */
    private String currentPeriod() {
        return YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    /** 账期由抄表时间推导，保证同一自然月的单据账期一致 */
    private String toPeriodMonth(LocalDateTime readingTime) {
        return YearMonth.from(readingTime).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    /** 抄表单号：CB + 时间戳 + 随机后缀，冲突时重试 */
    private String generateRecordNo() {
        String recordNo;
        do {
            recordNo = "CB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        } while (meterReadingRecordRepository.existsByRecordNo(recordNo));
        return recordNo;
    }

    private Map<Long, String> loadBuildingNames(Collection<Locker> lockers) {
        Set<Long> ids = lockers.stream().map(Locker::getBuildingId).collect(Collectors.toSet());
        return buildingRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Building::getId, Building::getName));
    }

    private Map<Long, String> loadUnitNames(Collection<Locker> lockers) {
        Set<Long> ids = lockers.stream().map(Locker::getUnitId).collect(Collectors.toSet());
        return unitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Unit::getId, Unit::getName));
    }
}
