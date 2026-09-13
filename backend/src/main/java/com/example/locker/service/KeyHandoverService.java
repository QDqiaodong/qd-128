package com.example.locker.service;

import com.example.locker.dto.*;
import com.example.locker.entity.Building;
import com.example.locker.entity.KeyBorrowRecord;
import com.example.locker.entity.KeyHandover;
import com.example.locker.entity.KeyHandoverItem;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.enums.KeyBorrowStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.KeyBorrowRecordRepository;
import com.example.locker.repository.KeyHandoverItemRepository;
import com.example.locker.repository.KeyHandoverRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 钥匙交接班：交班人在借用台账里对当前全部未还柜逐一点名，
 * 填写接班人和交接说明后一次提交。交接只留保管责任转移的痕迹，
 * 不触碰借用记录状态——被点名的记录仍是「借用中」，按柜一览未还条数不变。
 */
@Service
public class KeyHandoverService {

    @Autowired
    private KeyHandoverRepository keyHandoverRepository;

    @Autowired
    private KeyHandoverItemRepository keyHandoverItemRepository;

    @Autowired
    private KeyBorrowRecordRepository keyBorrowRecordRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    // ===================== 待点名清单 =====================

    /**
     * 交接窗口打开时的待点名清单：实时取全部「借用中」记录，
     * 与台账列表、按柜一览同源，保证刷新后名单与未还条数对得上。
     */
    public List<KeyHandoverPendingItemDTO> getPendingItems() {
        List<KeyBorrowRecord> openRecords =
                keyBorrowRecordRepository.findByStatusOrderByCreateTimeDesc(KeyBorrowStatus.ON_LOAN);
        if (openRecords.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Locker> lockerMap = loadLockers(
                openRecords.stream().map(KeyBorrowRecord::getLockerId).collect(Collectors.toSet()));
        Map<Long, String> buildingNames = loadBuildingNames(lockerMap.values());
        Map<Long, String> unitNames = loadUnitNames(lockerMap.values());
        LocalDateTime now = LocalDateTime.now();

        return openRecords.stream().map(record -> {
            KeyHandoverPendingItemDTO dto = new KeyHandoverPendingItemDTO();
            dto.setRecordId(record.getId());
            dto.setRecordNo(record.getRecordNo());
            dto.setLockerId(record.getLockerId());
            dto.setBorrower(record.getBorrower());
            dto.setReason(record.getReason());
            dto.setBorrowTime(record.getBorrowTime());
            dto.setExpectedReturnTime(record.getExpectedReturnTime());
            dto.setOverdue(record.getExpectedReturnTime() != null
                    && record.getExpectedReturnTime().isBefore(now));
            Locker locker = lockerMap.get(record.getLockerId());
            if (locker != null) {
                dto.setLockerNo(locker.getLockerNo());
                dto.setFloor(locker.getFloor());
                dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
                dto.setUnitName(unitNames.get(locker.getUnitId()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    // ===================== 提交交接 =====================

    /**
     * 提交交接班。整个交接（主记录 + 全部点名明细）在单个事务内一次落库：
     * 接班人、交班人、交接说明必填；勾选名单必须与提交时刻全部未还记录逐一对齐，
     * 勾漏、勾多（含已还/不存在的单）都整体回滚，不会留下半次交接。
     * 交接不改变任何借用记录状态：刷新后各柜仍显示借用中，未还条数不变。
     */
    @Transactional
    public KeyHandoverDTO submitHandover(KeyHandoverCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("交接内容不能为空");
        }
        if (!StringUtils.hasText(request.getHandoverFrom())) {
            throw new IllegalArgumentException("请填写交班人");
        }
        if (!StringUtils.hasText(request.getHandoverTo())) {
            throw new IllegalArgumentException("请填写接班人");
        }
        if (!StringUtils.hasText(request.getHandoverNote())) {
            throw new IllegalArgumentException("请填写交接说明");
        }
        List<Long> selectedIds = request.getRecordIds() == null ? Collections.emptyList()
                : request.getRecordIds().stream().filter(id -> id != null).collect(Collectors.toList());
        if (selectedIds.isEmpty()) {
            throw new IllegalArgumentException("请点名全部未还柜后再交班");
        }
        Set<Long> selectedSet = new HashSet<>(selectedIds);
        if (selectedSet.size() != selectedIds.size()) {
            throw new IllegalArgumentException("同一未还柜不能重复点名");
        }

        // 以提交时刻的台账为准重新取数，不能只信窗口打开时的名单（期间可能有新借出/归还）
        List<KeyBorrowRecord> openRecords =
                keyBorrowRecordRepository.findByStatusOrderByCreateTimeDesc(KeyBorrowStatus.ON_LOAN);
        if (openRecords.isEmpty()) {
            throw new IllegalArgumentException("当前没有未还柜，无需交班");
        }
        Map<Long, KeyBorrowRecord> openMap = openRecords.stream()
                .collect(Collectors.toMap(KeyBorrowRecord::getId, r -> r, (a, b) -> a));

        List<Long> missingIds = openRecords.stream()
                .map(KeyBorrowRecord::getId)
                .filter(id -> !selectedSet.contains(id))
                .collect(Collectors.toList());
        if (!missingIds.isEmpty()) {
            throw new IllegalArgumentException("还有 " + missingIds.size()
                    + " 个未还柜未点名，勾齐全部未还柜才能交班");
        }
        List<Long> extraIds = selectedIds.stream()
                .filter(id -> !openMap.containsKey(id))
                .collect(Collectors.toList());
        if (!extraIds.isEmpty()) {
            throw new IllegalArgumentException("勾选了已归还或不存在的借用记录，不能交班");
        }

        KeyHandover handover = new KeyHandover();
        handover.setHandoverNo(generateHandoverNo());
        handover.setHandoverFrom(request.getHandoverFrom().trim());
        handover.setHandoverTo(request.getHandoverTo().trim());
        handover.setHandoverNote(request.getHandoverNote().trim());
        handover.setItemCount(openRecords.size());
        keyHandoverRepository.save(handover);

        Map<Long, Locker> lockerMap = loadLockers(openRecords.stream()
                .map(KeyBorrowRecord::getLockerId).collect(Collectors.toSet()));
        List<KeyHandoverItem> items = new ArrayList<>();
        for (KeyBorrowRecord record : openRecords) {
            KeyHandoverItem item = new KeyHandoverItem();
            item.setHandoverId(handover.getId());
            item.setRecordId(record.getId());
            item.setLockerId(record.getLockerId());
            item.setBorrowerSnapshot(record.getBorrower());
            Locker locker = lockerMap.get(record.getLockerId());
            if (locker != null) {
                item.setLockerNo(locker.getLockerNo());
            }
            items.add(item);
        }
        keyHandoverItemRepository.saveAll(items);

        return toDTO(handover, items, openRecords, lockerMap);
    }

    // ===================== 交接痕迹查询 =====================

    /** 交接记录分页（最近交接在前），含每次点名明细 */
    public PageResponse<KeyHandoverDTO> getHandovers(Integer page, Integer size) {
        Page<KeyHandover> handoverPage = keyHandoverRepository.findAll(
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<KeyHandoverDTO> list = handoverPage.getContent().stream()
                .map(h -> toDTO(h, keyHandoverItemRepository.findByHandoverIdOrderByIdAsc(h.getId()), null, null))
                .collect(Collectors.toList());
        return new PageResponse<>(list, handoverPage.getTotalElements(), page, size);
    }

    public KeyHandoverDTO getHandover(Long id) {
        KeyHandover handover = keyHandoverRepository.findById(id).orElseThrow(() ->
                new RuntimeException("交接记录不存在: " + id));
        return toDTO(handover, keyHandoverItemRepository.findByHandoverIdOrderByIdAsc(id), null, null);
    }

    /** 某条借用记录被点名过的交接痕迹（台账详情/列表可据此展示「已交接×N」） */
    public List<KeyHandoverDTO> getRecordHandovers(Long recordId) {
        List<KeyHandoverItem> items = keyHandoverItemRepository
                .findByRecordIdIn(Collections.singleton(recordId));
        return groupItemsByHandover(items);
    }

    /** 某台柜体相关的全部交接痕迹（柜详情页留存） */
    public List<KeyHandoverDTO> getLockerHandovers(Long lockerId) {
        lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));
        List<KeyHandoverItem> items = keyHandoverItemRepository
                .findByLockerIdOrderByIdDesc(lockerId);
        return groupItemsByHandover(items);
    }

    // ===================== 转换与辅助 =====================

    private List<KeyHandoverDTO> groupItemsByHandover(List<KeyHandoverItem> items) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<KeyHandoverItem>> byHandover = items.stream()
                .collect(Collectors.groupingBy(KeyHandoverItem::getHandoverId, LinkedHashMap::new, Collectors.toList()));
        List<KeyHandover> handovers = keyHandoverRepository.findAllById(byHandover.keySet());
        Map<Long, KeyHandover> handoverMap = handovers.stream()
                .collect(Collectors.toMap(KeyHandover::getId, h -> h));
        List<KeyHandoverDTO> result = new ArrayList<>();
        for (Map.Entry<Long, List<KeyHandoverItem>> entry : byHandover.entrySet()) {
            KeyHandover handover = handoverMap.get(entry.getKey());
            if (handover != null) {
                result.add(toDTO(handover, entry.getValue(), null, null));
            }
        }
        result.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));
        return result;
    }

    private KeyHandoverDTO toDTO(KeyHandover handover, List<KeyHandoverItem> items,
                                 List<KeyBorrowRecord> recordsContext,
                                 Map<Long, Locker> lockerContext) {
        KeyHandoverDTO dto = new KeyHandoverDTO();
        dto.setId(handover.getId());
        dto.setHandoverNo(handover.getHandoverNo());
        dto.setHandoverFrom(handover.getHandoverFrom());
        dto.setHandoverTo(handover.getHandoverTo());
        dto.setHandoverNote(handover.getHandoverNote());
        dto.setItemCount(handover.getItemCount());
        dto.setCreateTime(handover.getCreateTime());
        if (items == null) {
            items = Collections.emptyList();
        }

        // 借用台账是借用状态的唯一数据源：明细上的柜体信息与「是否仍借用中」实时回查台账，
        // 交接快照只留存点名当时的借出人。保证刷新后明细标记与台账、未还条数对得上。
        List<Long> recordIds = items.stream().map(KeyHandoverItem::getRecordId).collect(Collectors.toList());
        Map<Long, KeyBorrowRecord> recordMap = recordsContext == null
                ? keyBorrowRecordRepository.findAllById(recordIds).stream()
                    .collect(Collectors.toMap(KeyBorrowRecord::getId, r -> r))
                : recordsContext.stream().collect(Collectors.toMap(KeyBorrowRecord::getId, r -> r, (a, b) -> a));
        Map<Long, Locker> lockerMap = lockerContext;
        if (lockerMap == null) {
            Set<Long> lockerIds = recordMap.values().stream()
                    .map(KeyBorrowRecord::getLockerId).collect(Collectors.toSet());
            lockerMap = loadLockers(lockerIds);
        }
        Map<Long, String> buildingNames = loadBuildingNames(lockerMap.values());
        Map<Long, String> unitNames = loadUnitNames(lockerMap.values());

        List<KeyHandoverItemDTO> itemDTOs = new ArrayList<>();
        for (KeyHandoverItem item : items) {
            KeyHandoverItemDTO itemDTO = new KeyHandoverItemDTO();
            itemDTO.setId(item.getId());
            itemDTO.setRecordId(item.getRecordId());
            itemDTO.setLockerId(item.getLockerId());
            itemDTO.setLockerNo(item.getLockerNo());
            itemDTO.setBorrower(item.getBorrowerSnapshot());
            KeyBorrowRecord record = recordMap.get(item.getRecordId());
            if (record != null) {
                itemDTO.setRecordNo(record.getRecordNo());
                itemDTO.setReason(record.getReason());
                itemDTO.setBorrowTime(record.getBorrowTime());
                itemDTO.setExpectedReturnTime(record.getExpectedReturnTime());
                // 交接不改状态：这里回查当前台账状态，归还后再看历史交接会显示已归还
                itemDTO.setOnLoan(record.getStatus() == KeyBorrowStatus.ON_LOAN);
            } else {
                itemDTO.setOnLoan(Boolean.FALSE);
            }
            Locker locker = lockerMap.get(item.getLockerId());
            if (locker != null) {
                if (itemDTO.getLockerNo() == null) {
                    itemDTO.setLockerNo(locker.getLockerNo());
                }
                itemDTO.setFloor(locker.getFloor());
                itemDTO.setBuildingName(buildingNames.get(locker.getBuildingId()));
                itemDTO.setUnitName(unitNames.get(locker.getUnitId()));
            }
            itemDTOs.add(itemDTO);
        }
        dto.setItems(itemDTOs);
        return dto;
    }

    /** 交接单号：JJ + 时间戳 + 随机后缀，冲突时重试 */
    private String generateHandoverNo() {
        String handoverNo;
        do {
            handoverNo = "JJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        } while (keyHandoverRepository.existsByHandoverNo(handoverNo));
        return handoverNo;
    }

    private Map<Long, Locker> loadLockers(Set<Long> lockerIds) {
        if (lockerIds == null || lockerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return lockerRepository.findAllById(lockerIds).stream()
                .collect(Collectors.toMap(Locker::getId, l -> l));
    }

    private Map<Long, String> loadBuildingNames(Collection<Locker> lockers) {
        if (lockers == null || lockers.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = lockers.stream().map(Locker::getBuildingId).collect(Collectors.toSet());
        return buildingRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Building::getId, Building::getName));
    }

    private Map<Long, String> loadUnitNames(Collection<Locker> lockers) {
        if (lockers == null || lockers.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = lockers.stream().map(Locker::getUnitId).collect(Collectors.toSet());
        return unitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Unit::getId, Unit::getName));
    }
}
