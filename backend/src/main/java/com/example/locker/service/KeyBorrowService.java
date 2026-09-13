package com.example.locker.service;

import com.example.locker.dto.*;
import com.example.locker.entity.Building;
import com.example.locker.entity.KeyBorrowRecord;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.enums.KeyBorrowStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.KeyBorrowRecordRepository;
import com.example.locker.repository.KeyHandoverItemRepository;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class KeyBorrowService {

    @Autowired
    private KeyBorrowRecordRepository keyBorrowRecordRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private KeyHandoverItemRepository keyHandoverItemRepository;

    // ===================== 借出登记 =====================

    /**
     * 登记钥匙借用。整个登记在单个事务内一次落库：
     * 全部校验通过后才保存，校验不通过则整体回滚，不会写出半条台账。
     * 同一柜钥匙存在未还记录时不允许再次借出；
     * 永久停用柜同样允许登记，用于补登历史借用。
     */
    @Transactional
    public KeyBorrowRecordDTO createRecord(KeyBorrowCreateRequest request) {
        if (request == null || request.getLockerId() == null) {
            throw new IllegalArgumentException("请选择借用钥匙的柜体");
        }
        Locker locker = lockerRepository.findById(request.getLockerId()).orElseThrow(() ->
                new IllegalArgumentException("快递柜不存在: " + request.getLockerId()));

        if (!StringUtils.hasText(request.getBorrower())) {
            throw new IllegalArgumentException("请填写借出人");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new IllegalArgumentException("请填写借用事由");
        }
        if (request.getBorrowTime() == null) {
            throw new IllegalArgumentException("请填写借出时间");
        }
        if (request.getBorrowTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("借出时间不能晚于当前时间");
        }
        if (request.getExpectedReturnTime() == null) {
            throw new IllegalArgumentException("请填写预计归还时间");
        }
        if (request.getExpectedReturnTime().isBefore(request.getBorrowTime())) {
            throw new IllegalArgumentException("预计归还时间不能早于借出时间");
        }
        if (keyBorrowRecordRepository.existsByLockerIdAndStatus(locker.getId(), KeyBorrowStatus.ON_LOAN)) {
            throw new IllegalArgumentException("该柜钥匙尚未归还，不能再次借出");
        }

        KeyBorrowRecord record = new KeyBorrowRecord();
        record.setRecordNo(generateRecordNo());
        record.setLockerId(locker.getId());
        record.setBorrower(request.getBorrower().trim());
        record.setReason(request.getReason().trim());
        record.setBorrowTime(request.getBorrowTime());
        record.setExpectedReturnTime(request.getExpectedReturnTime());
        record.setStatus(KeyBorrowStatus.ON_LOAN);
        record.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);

        return toDTO(keyBorrowRecordRepository.save(record));
    }

    // ===================== 归还登记 =====================

    /**
     * 归还钥匙：必须填写归还人；归还时间默认当前时间，补登历史归还时可指定过去时间，
     * 但不能早于借出时间。已归还的记录不能重复归还。
     */
    @Transactional
    public KeyBorrowRecordDTO returnRecord(Long id, KeyBorrowReturnRequest request) {
        if (request == null || !StringUtils.hasText(request.getReturner())) {
            throw new IllegalArgumentException("归还必须填写归还人");
        }
        KeyBorrowRecord record = keyBorrowRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("借用记录不存在: " + id));
        if (record.getStatus() == KeyBorrowStatus.RETURNED) {
            throw new IllegalArgumentException("该记录已归还，请勿重复操作");
        }

        LocalDateTime returnTime = request.getReturnTime() != null ? request.getReturnTime() : LocalDateTime.now();
        if (returnTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("归还时间不能晚于当前时间");
        }
        if (record.getBorrowTime() != null && returnTime.isBefore(record.getBorrowTime())) {
            throw new IllegalArgumentException("归还时间不能早于借出时间");
        }

        record.setStatus(KeyBorrowStatus.RETURNED);
        record.setReturner(request.getReturner().trim());
        record.setReturnTime(returnTime);
        return toDTO(keyBorrowRecordRepository.save(record));
    }

    // ===================== 借用改期 =====================

    /**
     * 借用改期：钥匙未还且预计归还刚好到点或已过点时，允许在原借用单上改一个更晚的预计归还
     * 并写明改期原因。已归还的单不能改；还没到预计归还时间（未来时间）的单直接拦下、不写任何数据。
     * 整个改期在单个事务内一次落库，任一校验不通过整体回滚，不会写出半条改期。
     * 改期只更新预计归还时间与改期痕迹，不改变借用状态，因此按柜一览的未还条数不会因改期减少。
     */
    @Transactional
    public KeyBorrowRecordDTO extendRecord(Long id, KeyBorrowExtendRequest request) {
        if (request == null || request.getExpectedReturnTime() == null) {
            throw new IllegalArgumentException("请选择新的预计归还时间");
        }
        if (!StringUtils.hasText(request.getExtendReason())) {
            throw new IllegalArgumentException("请填写改期原因");
        }
        KeyBorrowRecord record = keyBorrowRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("借用记录不存在: " + id));
        if (record.getStatus() == KeyBorrowStatus.RETURNED) {
            throw new IllegalArgumentException("该记录已归还，不能改期");
        }
        if (record.getExpectedReturnTime() == null
                || record.getExpectedReturnTime().isAfter(LocalDateTime.now())) {
            // 只拦预计归还还没到（未来时间）的单；刚好到点（等于当前时刻）及已过点都放行改期
            throw new IllegalArgumentException("还没到预计归还时间，不能改期");
        }
        if (!request.getExpectedReturnTime().isAfter(record.getExpectedReturnTime())) {
            throw new IllegalArgumentException("新的预计归还时间必须晚于原预计归还时间");
        }

        record.setExpectedReturnTime(request.getExpectedReturnTime());
        record.setExtendCount((record.getExtendCount() == null ? 0 : record.getExtendCount()) + 1);
        record.setLastExtendReason(request.getExtendReason().trim());
        record.setLastExtendTime(LocalDateTime.now());
        return toDTO(keyBorrowRecordRepository.save(record));
    }

    // ===================== 查询 =====================

    /**
     * 借用台账分页列表，可按状态（借用中/已归还）、柜体、单号/借出人/柜体编号关键字过滤。
     */
    public PageResponse<KeyBorrowRecordDTO> getRecords(Integer page, Integer size, String status,
                                                       Long lockerId, String keyword) {
        Specification<KeyBorrowRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), KeyBorrowStatus.fromCode(status.trim())));
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
                        cb.like(root.get("borrower"), like),
                        cb.like(root.get("reason"), like),
                        root.get("lockerId").in(sub)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<KeyBorrowRecord> recordPage = keyBorrowRecordRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<KeyBorrowRecordDTO> list = recordPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        fillHandoverCount(list);
        return new PageResponse<>(list, recordPage.getTotalElements(), page, size);
    }

    public KeyBorrowRecordDTO getRecord(Long id) {
        KeyBorrowRecord record = keyBorrowRecordRepository.findById(id).orElseThrow(() ->
                new RuntimeException("借用记录不存在: " + id));
        KeyBorrowRecordDTO dto = toDTO(record);
        fillHandoverCount(Collections.singletonList(dto));
        return dto;
    }

    /**
     * 某台柜体的全部借用记录（详情页留存历史）。
     */
    public List<KeyBorrowRecordDTO> getLockerRecords(Long lockerId) {
        lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));
        List<KeyBorrowRecordDTO> list = keyBorrowRecordRepository
                .findByLockerIdOrderByCreateTimeDesc(lockerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        fillHandoverCount(list);
        return list;
    }

    /**
     * 按柜钥匙状态一览：全部柜体（含停用柜）列出，借用中的排前面。
     * 借用标记与未还条数实时由借用台账推导，台账是唯一数据源，
     * 保证刷新后与柜体列表、详情的标记保持一致。
     */
    public List<LockerKeyBorrowOverviewDTO> getLockerOverview() {
        List<Locker> lockers = lockerRepository.findAll(Sort.by(Sort.Direction.ASC, "lockerNo"));
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Map<Long, List<KeyBorrowRecord>> byLocker = keyBorrowRecordRepository.findByLockerIdIn(lockerIds)
                .stream().collect(Collectors.groupingBy(KeyBorrowRecord::getLockerId));

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);

        List<LockerKeyBorrowOverviewDTO> result = new ArrayList<>();
        for (Locker locker : lockers) {
            List<KeyBorrowRecord> records = byLocker.getOrDefault(locker.getId(), Collections.emptyList());
            List<KeyBorrowRecord> open = records.stream()
                    .filter(r -> r.getStatus() == KeyBorrowStatus.ON_LOAN)
                    .collect(Collectors.toList());

            LockerKeyBorrowOverviewDTO dto = new LockerKeyBorrowOverviewDTO();
            dto.setLockerId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setStatus(status.name());
            dto.setStatusName(status.getDisplayName());
            dto.setOnLoan(!open.isEmpty());
            dto.setOpenRecordCount(open.size());
            dto.setTotalRecordCount(records.size());
            dto.setLastBorrowTime(records.stream()
                    .map(KeyBorrowRecord::getBorrowTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            result.add(dto);
        }

        // 借用中的排前面，便于优先跟进归还；其余按柜体编号排序
        result.sort(Comparator.comparing(LockerKeyBorrowOverviewDTO::getOnLoan).reversed()
                .thenComparing(LockerKeyBorrowOverviewDTO::getLockerNo));
        return result;
    }

    /**
     * 登记借用的可选柜体：包含全部生命周期状态，
     * 永久停用柜可补登历史借用，临时停用柜也可登记。
     */
    public List<KeyBorrowLockerOptionDTO> getLockerOptions() {
        List<Locker> lockers = lockerRepository.findAll(Sort.by(Sort.Direction.ASC, "lockerNo"));
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Set<Long> onLoanLockerIds = keyBorrowRecordRepository
                .findByLockerIdInAndStatus(lockerIds, KeyBorrowStatus.ON_LOAN).stream()
                .map(KeyBorrowRecord::getLockerId)
                .collect(Collectors.toSet());

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);

        return lockers.stream().map(locker -> {
            KeyBorrowLockerOptionDTO dto = new KeyBorrowLockerOptionDTO();
            dto.setId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setStatus(status.name());
            dto.setStatusName(status.getDisplayName());
            dto.setOnLoan(onLoanLockerIds.contains(locker.getId()));
            return dto;
        }).collect(Collectors.toList());
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (KeyBorrowStatus status : KeyBorrowStatus.values()) {
            map.put(status.name(), status.getDisplayName());
        }
        return map;
    }

    // ===================== 转换与辅助 =====================

    /**
     * 批量回填各借用记录被交接班点名的次数。交接只留痕迹不改借用状态，
     * 台账列表据此显示「已交接×N」，未还条数不受影响。
     */
    private void fillHandoverCount(List<KeyBorrowRecordDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        List<Long> recordIds = dtos.stream().map(KeyBorrowRecordDTO::getId).collect(Collectors.toList());
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] row : keyHandoverItemRepository.countByRecordIds(recordIds)) {
            countMap.put((Long) row[0], (Long) row[1]);
        }
        for (KeyBorrowRecordDTO dto : dtos) {
            dto.setHandoverCount(countMap.getOrDefault(dto.getId(), 0L).intValue());
        }
    }

    private KeyBorrowRecordDTO toDTO(KeyBorrowRecord record) {
        KeyBorrowRecordDTO dto = new KeyBorrowRecordDTO();
        dto.setId(record.getId());
        dto.setRecordNo(record.getRecordNo());
        dto.setLockerId(record.getLockerId());
        dto.setBorrower(record.getBorrower());
        dto.setReason(record.getReason());
        dto.setBorrowTime(record.getBorrowTime());
        dto.setExpectedReturnTime(record.getExpectedReturnTime());
        dto.setStatus(record.getStatus());
        dto.setStatusName(record.getStatus() == null ? null : record.getStatus().getDisplayName());
        // 借用中 = 钥匙未归还，列表与详情统一据此标记
        dto.setOnLoan(record.getStatus() == KeyBorrowStatus.ON_LOAN);
        LocalDateTime now = LocalDateTime.now();
        dto.setReturnOverdue(record.getStatus() == KeyBorrowStatus.ON_LOAN
                && record.getExpectedReturnTime() != null
                && record.getExpectedReturnTime().isBefore(now));
        // 刚好到点或已过点即可改期，与 extendRecord 的拦截口径一致，前端据此置灰/放行改期按钮
        dto.setExtendable(record.getStatus() == KeyBorrowStatus.ON_LOAN
                && record.getExpectedReturnTime() != null
                && !record.getExpectedReturnTime().isAfter(now));
        dto.setReturner(record.getReturner());
        dto.setReturnTime(record.getReturnTime());
        dto.setRemark(record.getRemark());
        dto.setExtendCount(record.getExtendCount() == null ? 0 : record.getExtendCount());
        dto.setLastExtendReason(record.getLastExtendReason());
        dto.setLastExtendTime(record.getLastExtendTime());
        dto.setHandoverCount(0);
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

    /** 台账编号：JY + 时间戳 + 随机后缀，冲突时重试 */
    private String generateRecordNo() {
        String recordNo;
        do {
            recordNo = "JY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        } while (keyBorrowRecordRepository.existsByRecordNo(recordNo));
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
