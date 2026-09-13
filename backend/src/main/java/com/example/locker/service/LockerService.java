package com.example.locker.service;

import com.example.locker.dto.*;
import com.example.locker.entity.AdjustmentRecord;
import com.example.locker.entity.ClearanceOrder;
import com.example.locker.entity.KeyBorrowRecord;
import com.example.locker.entity.Locker;
import com.example.locker.entity.MeterReadingRecord;
import com.example.locker.entity.RepairTicket;
import com.example.locker.entity.StatusChangeRecord;
import com.example.locker.enums.ClearanceStatus;
import com.example.locker.enums.KeyBorrowStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.enums.MeterReadingStatus;
import com.example.locker.enums.RepairStatus;
import com.example.locker.repository.AdjustmentRecordRepository;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.ClearanceOrderRepository;
import com.example.locker.repository.KeyBorrowRecordRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.MeterReadingRecordRepository;
import com.example.locker.repository.RepairTicketRepository;
import com.example.locker.repository.StatusChangeRecordRepository;
import com.example.locker.repository.UnitRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Service
public class LockerService {

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private AdjustmentRecordRepository adjustmentRecordRepository;

    @Autowired
    private StatusChangeRecordRepository statusChangeRecordRepository;

    @Autowired
    private ClearanceOrderRepository clearanceOrderRepository;

    @Autowired
    private KeyBorrowRecordRepository keyBorrowRecordRepository;

    @Autowired
    private MeterReadingRecordRepository meterReadingRecordRepository;

    @Autowired
    private RepairTicketRepository repairTicketRepository;

    @Autowired
    private SpecTemplateService specTemplateService;

    public LockerDTO convertToDTO(Locker locker) {
        LockerDTO dto = new LockerDTO();
        dto.setId(locker.getId());
        dto.setLockerNo(locker.getLockerNo());
        dto.setCompartmentCount(locker.getCompartmentCount());
        dto.setSpecType(locker.getSpecType());
        dto.setSpecTypeName(specTemplateService.getSpecTypeName(locker.getSpecType()));
        dto.setBuildingId(locker.getBuildingId());
        dto.setUnitId(locker.getUnitId());
        dto.setFloor(locker.getFloor());
        dto.setInstallationDate(locker.getInstallationDate());
        dto.setRemark(locker.getRemark());
        dto.setCreateTime(locker.getCreateTime());
        dto.setUpdateTime(locker.getUpdateTime());

        LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
        dto.setStatus(status.name());
        dto.setStatusName(status.getDisplayName());

        buildingRepository.findById(locker.getBuildingId()).ifPresent(building ->
                dto.setBuildingName(building.getName()));
        unitRepository.findById(locker.getUnitId()).ifPresent(unit ->
                dto.setUnitName(unit.getName()));

        return dto;
    }

    public PageResponse<LockerDTO> getLockers(Integer page, Integer size) {
        return getLockers(page, size, null);
    }

    /**
     * 管理端柜体列表：默认展示全部状态，可按状态集合过滤。
     */
    public PageResponse<LockerDTO> getLockers(Integer page, Integer size, List<String> statuses) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));

        Page<Locker> lockerPage;
        List<LockerStatus> parsed = parseStatuses(statuses);
        if (parsed == null) {
            lockerPage = lockerRepository.findAll(pageable);
        } else {
            Specification<Locker> spec = (root, query, cb) ->
                    root.get("status").in(parsed);
            lockerPage = lockerRepository.findAll(spec, pageable);
        }

        List<LockerDTO> dtoList = lockerPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        fillClearanceInfo(dtoList);
        fillKeyBorrowInfo(dtoList);
        fillMeterReadingInfo(dtoList);
        fillRepairInfo(dtoList);
        return new PageResponse<>(dtoList, lockerPage.getTotalElements(), page, size);
    }

    public LockerDTO getLockerById(Long id) {
        Locker locker = lockerRepository.findById(id).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + id));
        LockerDTO dto = convertToDTO(locker);
        fillClearanceInfo(java.util.Collections.singletonList(dto));
        fillKeyBorrowInfo(java.util.Collections.singletonList(dto));
        fillMeterReadingInfo(java.util.Collections.singletonList(dto));
        fillRepairInfo(java.util.Collections.singletonList(dto));
        return dto;
    }

    /**
     * 滞留标记、在办单数与滞留件数实时由办理中的清柜单推导，
     * 清柜单是唯一数据源，保证刷新后柜体标记与清柜单状态、件数一致。
     */
    private void fillClearanceInfo(List<LockerDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        List<Long> lockerIds = dtos.stream().map(LockerDTO::getId).collect(Collectors.toList());
        Map<Long, List<ClearanceOrder>> openByLocker = clearanceOrderRepository
                .findByLockerIdInAndStatus(lockerIds, ClearanceStatus.PROCESSING).stream()
                .collect(Collectors.groupingBy(ClearanceOrder::getLockerId));
        for (LockerDTO dto : dtos) {
            List<ClearanceOrder> open = openByLocker.getOrDefault(dto.getId(),
                    java.util.Collections.emptyList());
            dto.setOverdue(!open.isEmpty());
            dto.setOpenClearanceCount(open.size());
            dto.setOverduePackageCount(open.stream()
                    .mapToInt(o -> o.getPackageCount() == null ? 0 : o.getPackageCount()).sum());
        }
    }

    /**
     * 钥匙借用标记与未还条数实时由借用中的台账记录推导，
     * 借用台账是唯一数据源，保证刷新后柜体标记与台账状态一致。
     */
    private void fillKeyBorrowInfo(List<LockerDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        List<Long> lockerIds = dtos.stream().map(LockerDTO::getId).collect(Collectors.toList());
        Map<Long, List<KeyBorrowRecord>> openByLocker = keyBorrowRecordRepository
                .findByLockerIdInAndStatus(lockerIds, KeyBorrowStatus.ON_LOAN).stream()
                .collect(Collectors.groupingBy(KeyBorrowRecord::getLockerId));
        for (LockerDTO dto : dtos) {
            List<KeyBorrowRecord> open = openByLocker.getOrDefault(dto.getId(),
                    java.util.Collections.emptyList());
            dto.setKeyBorrowed(!open.isEmpty());
            dto.setOpenKeyBorrowCount(open.size());
        }
    }

    /**
     * 本月已抄标记实时由当前自然月的有效抄表单推导，
     * 抄表单是唯一数据源，保证刷新后柜体列表/详情的已抄标记
     * 与抄表页本月已抄台数、柜体页读数保持一致。
     */
    private void fillMeterReadingInfo(List<LockerDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        String currentPeriod = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<Long> lockerIds = dtos.stream().map(LockerDTO::getId).collect(Collectors.toList());
        java.util.Set<Long> readLockerIds = meterReadingRecordRepository
                .findByLockerIdInAndPeriodMonthAndStatus(lockerIds, currentPeriod, MeterReadingStatus.ACTIVE)
                .stream().map(MeterReadingRecord::getLockerId)
                .collect(Collectors.toSet());
        for (LockerDTO dto : dtos) {
            dto.setMeterReadThisMonth(readLockerIds.contains(dto.getId()));
        }
    }

    /**
     * 维修中标记与处理中报修条数实时由处理中的报修单推导，
     * 报修台账是唯一数据源，保证刷新后柜体列表/详情的可用标记
     * 与报修台账列表、柜详情报修条数保持一致。
     */
    private void fillRepairInfo(List<LockerDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        List<Long> lockerIds = dtos.stream().map(LockerDTO::getId).collect(Collectors.toList());
        Map<Long, List<RepairTicket>> openByLocker = repairTicketRepository
                .findByLockerIdInAndStatus(lockerIds, RepairStatus.PROCESSING).stream()
                .collect(Collectors.groupingBy(RepairTicket::getLockerId));
        for (LockerDTO dto : dtos) {
            List<RepairTicket> open = openByLocker.getOrDefault(dto.getId(),
                    java.util.Collections.emptyList());
            dto.setRepairing(!open.isEmpty());
            dto.setOpenRepairCount(open.size());
        }
    }

    @Transactional
    public LockerDTO createLocker(LockerCreateRequest request) {
        if (lockerRepository.existsByLockerNo(request.getLockerNo())) {
            throw new RuntimeException("柜体编号已存在: " + request.getLockerNo());
        }

        Locker locker = new Locker();
        locker.setLockerNo(request.getLockerNo());
        locker.setCompartmentCount(request.getCompartmentCount());
        locker.setSpecType(request.getSpecType());
        locker.setBuildingId(request.getBuildingId());
        locker.setUnitId(request.getUnitId());
        locker.setFloor(request.getFloor());
        locker.setInstallationDate(request.getInstallationDate());
        locker.setRemark(request.getRemark());
        locker.setStatus(LockerStatus.ACTIVE);

        Locker saved = lockerRepository.save(locker);
        return convertToDTO(saved);
    }

    @Transactional
    public LockerDTO updateLocker(Long id, LockerUpdateRequest request) {
        Locker existing = lockerRepository.findById(id).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + id));

        if (request.getLockerNo() != null && !request.getLockerNo().equals(existing.getLockerNo())) {
            if (lockerRepository.existsByLockerNo(request.getLockerNo())) {
                throw new RuntimeException("柜体编号已存在: " + request.getLockerNo());
            }
            existing.setLockerNo(request.getLockerNo());
        }

        if (request.getCompartmentCount() != null) {
            existing.setCompartmentCount(request.getCompartmentCount());
        }
        if (request.getSpecType() != null) {
            existing.setSpecType(request.getSpecType());
        }
        if (request.getBuildingId() != null) {
            existing.setBuildingId(request.getBuildingId());
        }
        if (request.getUnitId() != null) {
            existing.setUnitId(request.getUnitId());
        }
        if (request.getFloor() != null) {
            existing.setFloor(request.getFloor());
        }
        if (request.getInstallationDate() != null) {
            existing.setInstallationDate(request.getInstallationDate());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }

        Locker saved = lockerRepository.save(existing);
        return convertToDTO(saved);
    }

    @Transactional
    public void deleteLocker(Long id) {
        lockerRepository.deleteById(id);
    }

    /**
     * 多条件筛选（分页）。停用柜体默认不出现，除非显式指定 statuses。
     */
    public PageResponse<LockerDTO> filterLockers(FilterRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        Page<Locker> lockerPage = lockerRepository.findAll(buildFilterSpec(request, true), pageable);
        List<LockerDTO> dtoList = lockerPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return new PageResponse<>(dtoList, lockerPage.getTotalElements(), request.getPage(), request.getSize());
    }

    /**
     * 供归档使用的多条件筛选（不分页）。停用柜体默认不出现，除非显式指定 statuses。
     */
    public List<LockerDTO> filterLockersForArchive(FilterRequest request) {
        List<Locker> lockers = lockerRepository.findAll(buildFilterSpec(request, true));
        return lockers.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private Specification<Locker> buildFilterSpec(FilterRequest request, boolean excludeDisabledByDefault) {
        List<LockerStatus> parsed = parseStatuses(request.getStatuses());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getBuildingIds() != null && !request.getBuildingIds().isEmpty()) {
                predicates.add(root.get("buildingId").in(request.getBuildingIds()));
            }

            if (request.getUnitIds() != null && !request.getUnitIds().isEmpty()) {
                predicates.add(root.get("unitId").in(request.getUnitIds()));
            }

            if (request.getSpecTypes() != null && !request.getSpecTypes().isEmpty()) {
                predicates.add(root.get("specType").in(request.getSpecTypes()));
            }

            if (StringUtils.hasText(request.getStartDate())) {
                LocalDate startDate = LocalDate.parse(request.getStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                predicates.add(cb.greaterThanOrEqualTo(root.get("installationDate"), startDate));
            }

            if (StringUtils.hasText(request.getEndDate())) {
                LocalDate endDate = LocalDate.parse(request.getEndDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                predicates.add(cb.lessThanOrEqualTo(root.get("installationDate"), endDate));
            }

            if (parsed != null) {
                predicates.add(root.get("status").in(parsed));
            } else if (excludeDisabledByDefault) {
                // 多条件筛选默认只返回正常柜体
                predicates.add(cb.equal(root.get("status"), LockerStatus.ACTIVE));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 解析状态过滤参数：null/空返回 null（表示不按状态过滤），非法值抛出异常。
     */
    private List<LockerStatus> parseStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        List<LockerStatus> result = new ArrayList<>();
        for (String code : statuses) {
            if (StringUtils.hasText(code)) {
                result.add(LockerStatus.fromCode(code.trim()));
            }
        }
        return result.isEmpty() ? null : result;
    }

    public List<AdjustmentRecord> getAdjustmentRecords(Long lockerId) {
        List<AdjustmentRecord> records =
                adjustmentRecordRepository.findByLockerIdOrderByAdjustTimeDesc(lockerId);
        records.forEach(this::fillAdjustmentNames);
        return records;
    }

    private void fillAdjustmentNames(AdjustmentRecord record) {
        if (record.getOldBuildingId() != null) {
            buildingRepository.findById(record.getOldBuildingId())
                    .ifPresent(b -> record.setOldBuildingName(b.getName()));
        }
        if (record.getOldUnitId() != null) {
            unitRepository.findById(record.getOldUnitId())
                    .ifPresent(u -> record.setOldUnitName(u.getName()));
        }
        buildingRepository.findById(record.getNewBuildingId())
                .ifPresent(b -> record.setNewBuildingName(b.getName()));
        unitRepository.findById(record.getNewUnitId())
                .ifPresent(u -> record.setNewUnitName(u.getName()));
    }

    @Transactional
    public AdjustmentRecord adjustLocker(Long lockerId, AdjustRequest request) {
        Locker locker = lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));

        AdjustmentRecord record = new AdjustmentRecord();
        record.setLockerId(lockerId);
        record.setOldBuildingId(locker.getBuildingId());
        record.setOldUnitId(locker.getUnitId());
        record.setNewBuildingId(request.getNewBuildingId());
        record.setNewUnitId(request.getNewUnitId());
        record.setReason(request.getReason());
        record.setOperator(request.getOperator());

        locker.setBuildingId(request.getNewBuildingId());
        locker.setUnitId(request.getNewUnitId());
        lockerRepository.save(locker);

        AdjustmentRecord saved = adjustmentRecordRepository.save(record);
        fillAdjustmentNames(saved);
        return saved;
    }

    // ===================== 生命周期状态管理 =====================

    /**
     * 变更柜体生命周期状态并记录前后状态、原因、操作人及时间。
     */
    @Transactional
    public StatusChangeRecord changeLockerStatus(Long lockerId, StatusChangeRequest request) {
        if (request == null || !StringUtils.hasText(request.getTargetStatus())) {
            throw new IllegalArgumentException("请选择目标状态");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new IllegalArgumentException("请填写状态变更原因");
        }

        Locker locker = lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));

        LockerStatus target;
        try {
            target = LockerStatus.fromCode(request.getTargetStatus().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        LockerStatus current = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
        validateTransition(current, target);

        String operator = StringUtils.hasText(request.getOperator())
                ? request.getOperator().trim() : "系统管理员";

        StatusChangeRecord record = new StatusChangeRecord();
        record.setLockerId(lockerId);
        record.setOldStatus(current);
        record.setNewStatus(target);
        record.setReason(request.getReason().trim());
        record.setOperator(operator);

        locker.setStatus(target);
        lockerRepository.save(locker);

        return statusChangeRecordRepository.save(record);
    }

    /**
     * 合法流转：
     * 正常 -> 临时停用 / 永久停用
     * 临时停用 -> 正常(恢复) / 永久停用
     * 永久停用为终态，不可再变更。
     */
    private void validateTransition(LockerStatus from, LockerStatus to) {
        if (from == to) {
            throw new IllegalArgumentException("柜体当前已是「" + from.getDisplayName() + "」状态，无需重复变更");
        }
        if (from == LockerStatus.PERMANENTLY_DISABLED) {
            throw new IllegalArgumentException("柜体已永久停用，为终态，不可恢复或变更");
        }
        EnumSet<LockerStatus> allowed;
        if (from == LockerStatus.ACTIVE) {
            allowed = EnumSet.of(LockerStatus.TEMPORARILY_DISABLED, LockerStatus.PERMANENTLY_DISABLED);
        } else {
            allowed = EnumSet.of(LockerStatus.ACTIVE, LockerStatus.PERMANENTLY_DISABLED);
        }
        if (!allowed.contains(to)) {
            throw new IllegalArgumentException(
                    "不允许从「" + from.getDisplayName() + "」变更为「" + to.getDisplayName() + "」");
        }
    }

    /**
     * 查询柜体完整状态变更记录，可按变更后状态筛选。
     */
    public List<StatusChangeRecord> getStatusChangeRecords(Long lockerId, String status) {
        if (StringUtils.hasText(status)) {
            LockerStatus target = LockerStatus.fromCode(status.trim());
            return statusChangeRecordRepository
                    .findByLockerIdAndNewStatusOrderByChangeTimeDesc(lockerId, target);
        }
        return statusChangeRecordRepository.findByLockerIdOrderByChangeTimeDesc(lockerId);
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (LockerStatus status : LockerStatus.values()) {
            map.put(status.name(), status.getDisplayName());
        }
        return map;
    }

    public List<String> getSpecTypes() {
        return new ArrayList<>(specTemplateService.getSpecTemplate().keySet());
    }

    public Map<String, String> getSpecTypeMap() {
        return specTemplateService.getSpecTemplate();
    }

    public long countLockers() {
        return lockerRepository.count();
    }

    public long countLockersByBuilding(Long buildingId) {
        return lockerRepository.findByBuildingId(buildingId).size();
    }
}
