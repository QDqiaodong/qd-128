package com.example.locker.service;

import com.example.locker.dto.*;
import com.example.locker.entity.Building;
import com.example.locker.entity.ClearanceOrder;
import com.example.locker.entity.ClearanceUrgeRecord;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.enums.ClearanceStatus;
import com.example.locker.enums.ClearanceUrgeStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.ClearanceOrderRepository;
import com.example.locker.repository.ClearanceUrgeRecordRepository;
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
public class ClearanceService {

    @Autowired
    private ClearanceOrderRepository clearanceOrderRepository;

    @Autowired
    private ClearanceUrgeRecordRepository urgeRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    // ===================== 登记 =====================

    /**
     * 登记滞留件清柜单。整个登记在单个事务内一次落库：
     * 校验不通过则整体回滚，不会留下只写了一半的单据。
     * 永久停用柜同样允许登记，用于补登历史滞留。
     */
    @Transactional
    public ClearanceOrderDTO createOrder(ClearanceOrderCreateRequest request) {
        if (request == null || request.getLockerId() == null) {
            throw new IllegalArgumentException("请选择滞留件所在柜体");
        }
        Locker locker = lockerRepository.findById(request.getLockerId()).orElseThrow(() ->
                new IllegalArgumentException("快递柜不存在: " + request.getLockerId()));

        if (!StringUtils.hasText(request.getOverdueCompartments())) {
            throw new IllegalArgumentException("请填写滞留格口");
        }
        if (request.getPackageCount() == null || request.getPackageCount() < 1) {
            throw new IllegalArgumentException("滞留件数必须大于 0");
        }
        if (request.getFoundTime() == null) {
            throw new IllegalArgumentException("请填写发现时间");
        }
        if (request.getFoundTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("发现时间不能晚于当前时间");
        }
        if (!StringUtils.hasText(request.getHandler())) {
            throw new IllegalArgumentException("请填写处理人");
        }

        ClearanceOrder order = new ClearanceOrder();
        order.setOrderNo(generateOrderNo());
        order.setLockerId(locker.getId());
        order.setOverdueCompartments(request.getOverdueCompartments().trim());
        order.setPackageCount(request.getPackageCount());
        order.setFoundTime(request.getFoundTime());
        order.setHandler(request.getHandler().trim());
        order.setStatus(ClearanceStatus.PROCESSING);
        order.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);

        return toDTO(clearanceOrderRepository.save(order), false, new UrgeStats(0, false), Collections.emptyList());
    }

    // ===================== 办结 =====================

    /**
     * 办结清柜单：必须填写处理结果；已办结的单据不能重复办结。
     * 办结时该单若仍有未关闭催领，在同一事务内自动关闭，
     * 保证不会出现「单据已办结却还挂着未关闭催领」的对不上。
     */
    @Transactional
    public ClearanceOrderDTO completeOrder(Long id, ClearanceCompleteRequest request) {
        if (request == null || !StringUtils.hasText(request.getHandleResult())) {
            throw new IllegalArgumentException("办结必须填写处理结果");
        }
        ClearanceOrder order = clearanceOrderRepository.findByIdForUpdate(id).orElseThrow(() ->
                new RuntimeException("清柜单不存在: " + id));
        if (order.getStatus() == ClearanceStatus.COMPLETED) {
            throw new IllegalArgumentException("该清柜单已办结，请勿重复操作");
        }

        closeOpenUrges(order, null, null, true);

        order.setStatus(ClearanceStatus.COMPLETED);
        order.setHandleResult(request.getHandleResult().trim());
        order.setCompleteTime(LocalDateTime.now());
        return toDTO(clearanceOrderRepository.save(order), true);
    }

    // ===================== 当面催领 =====================

    /**
     * 登记一笔当面催领：办理中的单才能登记，催领时间与经办人必填；
     * 同一张办理中的单已有未关闭催领时拒绝再记，关闭后才能再记。
     * 行锁 + 事务保证并发下也不会同时挂两笔未关闭催领，校验失败整体回滚，不写半条。
     */
    @Transactional
    public ClearanceUrgeRecordDTO createUrge(Long orderId, ClearanceUrgeCreateRequest request) {
        ClearanceOrder order = clearanceOrderRepository.findByIdForUpdate(orderId).orElseThrow(() ->
                new RuntimeException("清柜单不存在: " + orderId));
        if (order.getStatus() != ClearanceStatus.PROCESSING) {
            throw new IllegalArgumentException("清柜单已办结，不能再登记催领");
        }
        if (request == null || !StringUtils.hasText(request.getOperator())) {
            throw new IllegalArgumentException("请填写经办人");
        }
        LocalDateTime urgeTime = request.getUrgeTime() == null ? LocalDateTime.now() : request.getUrgeTime();
        if (urgeTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("催领时间不能晚于当前时间");
        }
        if (urgeTime.isBefore(order.getFoundTime())) {
            throw new IllegalArgumentException("催领时间不能早于清柜单发现时间");
        }
        if (findOpenUrge(orderId) != null) {
            throw new IllegalArgumentException("该单已有未关闭的当面催领，请先关闭后再登记");
        }

        ClearanceUrgeRecord record = new ClearanceUrgeRecord();
        record.setOrderId(order.getId());
        record.setUrgeTime(urgeTime);
        record.setOperator(request.getOperator().trim());
        record.setStatus(ClearanceUrgeStatus.OPEN);
        return toUrgeDTO(urgeRepository.save(record));
    }

    /**
     * 关闭一笔未关闭的当面催领；关闭后同一张单才能再记下一笔。
     */
    @Transactional
    public ClearanceUrgeRecordDTO closeUrgeByUrgeId(Long urgeId, ClearanceUrgeCloseRequest request) {
        ClearanceUrgeRecord record = urgeRepository.findById(urgeId).orElseThrow(() ->
                new RuntimeException("催领记录不存在: " + urgeId));
        return closeUrge(record.getOrderId(), urgeId, request);
    }

    /**
     * 关闭一笔未关闭的当面催领；关闭后同一张单才能再记下一笔。
     */
    @Transactional
    public ClearanceUrgeRecordDTO closeUrge(Long orderId, Long urgeId, ClearanceUrgeCloseRequest request) {
        ClearanceOrder order = clearanceOrderRepository.findByIdForUpdate(orderId).orElseThrow(() ->
                new RuntimeException("清柜单不存在: " + orderId));
        ClearanceUrgeRecord record = urgeRepository.findById(urgeId).orElseThrow(() ->
                new RuntimeException("催领记录不存在: " + urgeId));
        if (!record.getOrderId().equals(order.getId())) {
            throw new IllegalArgumentException("催领记录不属于该清柜单");
        }
        if (record.getStatus() == ClearanceUrgeStatus.CLOSED) {
            throw new IllegalArgumentException("该催领记录已关闭，请勿重复操作");
        }

        record.setStatus(ClearanceUrgeStatus.CLOSED);
        record.setCloseTime(LocalDateTime.now());
        record.setCloseNote(request != null && StringUtils.hasText(request.getCloseNote())
                ? request.getCloseNote().trim() : null);
        record.setCloseOperator(request != null && StringUtils.hasText(request.getCloseOperator())
                ? request.getCloseOperator().trim() : "系统管理员");
        record.setAutoClosed(false);
        return toUrgeDTO(urgeRepository.save(record));
    }

    /**
     * 某张清柜单的当面催领台账（按催领时间倒序）。
     */
    public List<ClearanceUrgeRecordDTO> getUrges(Long orderId) {
        clearanceOrderRepository.findById(orderId).orElseThrow(() ->
                new RuntimeException("清柜单不存在: " + orderId));
        return urgeRepository.findByOrderIdOrderByUrgeTimeDescIdDesc(orderId).stream()
                .map(this::toUrgeDTO)
                .collect(Collectors.toList());
    }

    private ClearanceUrgeRecord findOpenUrge(Long orderId) {
        return urgeRepository
                .findFirstByOrderIdAndStatusOrderByIdAsc(orderId, ClearanceUrgeStatus.OPEN)
                .orElse(null);
    }

    /** 关闭该单当前所有未关闭催领（办结自动关闭时关闭说明留痕） */
    private void closeOpenUrges(ClearanceOrder order, String closeOperator, String closeNote, boolean auto) {
        List<ClearanceUrgeRecord> records = urgeRepository
                .findByOrderIdOrderByUrgeTimeDescIdDesc(order.getId());
        LocalDateTime now = LocalDateTime.now();
        for (ClearanceUrgeRecord record : records) {
            if (record.getStatus() != ClearanceUrgeStatus.OPEN) {
                continue;
            }
            record.setStatus(ClearanceUrgeStatus.CLOSED);
            record.setCloseTime(now);
            record.setAutoClosed(auto);
            record.setCloseOperator(StringUtils.hasText(closeOperator) ? closeOperator.trim() : "系统管理员");
            if (StringUtils.hasText(closeNote)) {
                record.setCloseNote(closeNote.trim());
            } else if (auto) {
                record.setCloseNote("清柜单办结，系统自动关闭未关闭催领");
            }
            urgeRepository.save(record);
        }
    }

    // ===================== 查询 =====================

    /**
     * 清柜单分页列表，可按状态（办理中/已办结）、柜体、单号/柜体编号关键字过滤。
     */
    public PageResponse<ClearanceOrderDTO> getOrders(Integer page, Integer size, String status,
                                                     Long lockerId, String keyword) {
        Specification<ClearanceOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), ClearanceStatus.fromCode(status.trim())));
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
                        cb.like(root.get("orderNo"), like),
                        cb.like(root.get("overdueCompartments"), like),
                        root.get("lockerId").in(sub)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ClearanceOrder> orderPage = clearanceOrderRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<ClearanceOrder> orders = orderPage.getContent();
        Map<Long, List<ClearanceUrgeRecord>> urgeMap = loadUrgesByOrder(orders.stream()
                .map(ClearanceOrder::getId).collect(Collectors.toList()));
        List<ClearanceOrderDTO> list = orders.stream()
                .map(o -> toDTO(o, false, urgeStatsFor(o.getId(), urgeMap), urgeMap.get(o.getId())))
                .collect(Collectors.toList());
        return new PageResponse<>(list, orderPage.getTotalElements(), page, size);
    }

    public ClearanceOrderDTO getOrder(Long id) {
        ClearanceOrder order = clearanceOrderRepository.findById(id).orElseThrow(() ->
                new RuntimeException("清柜单不存在: " + id));
        return toDTO(order, true, null, null);
    }

    /**
     * 某台柜体的全部清柜单（详情页留存历史）。
     */
    public List<ClearanceOrderDTO> getLockerOrders(Long lockerId) {
        lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));
        List<ClearanceOrder> orders = clearanceOrderRepository.findByLockerIdOrderByCreateTimeDesc(lockerId);
        Map<Long, List<ClearanceUrgeRecord>> urgeMap = loadUrgesByOrder(orders.stream()
                .map(ClearanceOrder::getId).collect(Collectors.toList()));
        return orders.stream()
                .map(o -> toDTO(o, false, urgeStatsFor(o.getId(), urgeMap), urgeMap.get(o.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 按柜滞留一览：正常柜全量列出。有办理中清柜单的标记「滞留中」，
     * 没有任何办理中单据的显示「未登记」，正常柜漏登滞留登记时能在列表中直接看出。
     */
    public List<LockerClearanceOverviewDTO> getLockerOverview() {
        List<Locker> lockers = lockerRepository.findByStatus(LockerStatus.ACTIVE);
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Map<Long, List<ClearanceOrder>> byLocker = clearanceOrderRepository.findByLockerIdIn(lockerIds)
                .stream().collect(Collectors.groupingBy(ClearanceOrder::getLockerId));

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);

        List<LockerClearanceOverviewDTO> result = new ArrayList<>();
        for (Locker locker : lockers) {
            List<ClearanceOrder> orders = byLocker.getOrDefault(locker.getId(), Collections.emptyList());
            List<ClearanceOrder> open = orders.stream()
                    .filter(o -> o.getStatus() == ClearanceStatus.PROCESSING)
                    .collect(Collectors.toList());

            LockerClearanceOverviewDTO dto = new LockerClearanceOverviewDTO();
            dto.setLockerId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            dto.setOverdue(!open.isEmpty());
            dto.setOpenOrderCount(open.size());
            dto.setOpenPackageCount(open.stream()
                    .mapToInt(o -> o.getPackageCount() == null ? 0 : o.getPackageCount()).sum());
            dto.setTotalOrderCount(orders.size());
            dto.setLastFoundTime(orders.stream()
                    .map(ClearanceOrder::getFoundTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            result.add(dto);
        }

        // 滞留中的排前面，便于优先处理；其余按柜体编号排序
        result.sort(Comparator.comparing(LockerClearanceOverviewDTO::getOverdue).reversed()
                .thenComparing(LockerClearanceOverviewDTO::getLockerNo));
        return result;
    }

    /**
     * 登记清柜单的可选柜体：包含全部生命周期状态，
     * 永久停用柜可补登历史滞留，临时停用柜也可登记。
     */
    public List<ClearanceLockerOptionDTO> getLockerOptions() {
        List<Locker> lockers = lockerRepository.findAll(Sort.by(Sort.Direction.ASC, "lockerNo"));
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Set<Long> overdueLockerIds = clearanceOrderRepository
                .findByLockerIdInAndStatus(lockerIds, ClearanceStatus.PROCESSING).stream()
                .map(ClearanceOrder::getLockerId)
                .collect(Collectors.toSet());

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);

        return lockers.stream().map(locker -> {
            ClearanceLockerOptionDTO dto = new ClearanceLockerOptionDTO();
            dto.setId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setStatus(status.name());
            dto.setStatusName(status.getDisplayName());
            dto.setOverdue(overdueLockerIds.contains(locker.getId()));
            return dto;
        }).collect(Collectors.toList());
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (ClearanceStatus status : ClearanceStatus.values()) {
            map.put(status.name(), status.getDisplayName());
        }
        return map;
    }

    // ===================== 转换与辅助 =====================

    /** 一张单的催领汇总：总次数 + 是否存在未关闭催领 */
    private static class UrgeStats {
        final int count;
        final boolean open;

        UrgeStats(int count, boolean open) {
            this.count = count;
            this.open = open;
        }
    }

    private Map<Long, List<ClearanceUrgeRecord>> loadUrgesByOrder(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return urgeRepository.findByOrderIdInOrderByUrgeTimeDescIdDesc(orderIds).stream()
                .collect(Collectors.groupingBy(ClearanceUrgeRecord::getOrderId));
    }

    private UrgeStats urgeStatsFor(Long orderId, Map<Long, List<ClearanceUrgeRecord>> urgeMap) {
        List<ClearanceUrgeRecord> records = urgeMap.getOrDefault(orderId, Collections.emptyList());
        boolean open = records.stream().anyMatch(r -> r.getStatus() == ClearanceUrgeStatus.OPEN);
        return new UrgeStats(records.size(), open);
    }

    private ClearanceOrderDTO toDTO(ClearanceOrder order, boolean withUrgeRecords) {
        List<ClearanceUrgeRecord> records = withUrgeRecords
                ? urgeRepository.findByOrderIdOrderByUrgeTimeDescIdDesc(order.getId())
                : Collections.emptyList();
        UrgeStats stats = new UrgeStats(records.size(),
                records.stream().anyMatch(r -> r.getStatus() == ClearanceUrgeStatus.OPEN));
        return toDTO(order, withUrgeRecords, stats, withUrgeRecords ? records : null);
    }

    private ClearanceOrderDTO toDTO(ClearanceOrder order, boolean withUrgeRecords,
                                    UrgeStats urgeStats, List<ClearanceUrgeRecord> urgeRecords) {
        ClearanceOrderDTO dto = new ClearanceOrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setLockerId(order.getLockerId());
        dto.setOverdueCompartments(order.getOverdueCompartments());
        dto.setPackageCount(order.getPackageCount());
        dto.setFoundTime(order.getFoundTime());
        dto.setHandler(order.getHandler());
        dto.setStatus(order.getStatus());
        dto.setStatusName(order.getStatus() == null ? null : order.getStatus().getDisplayName());
        // 办理中 = 柜体处于滞留中，列表与详情统一据此标记；催领不改变单据状态
        dto.setOverdue(order.getStatus() == ClearanceStatus.PROCESSING);
        dto.setHandleResult(order.getHandleResult());
        dto.setCompleteTime(order.getCompleteTime());
        dto.setRemark(order.getRemark());
        dto.setCreateTime(order.getCreateTime());
        dto.setUpdateTime(order.getUpdateTime());

        // 催领次数与未关闭标记全部以催领台账实时推导
        UrgeStats stats = urgeStats != null ? urgeStats : new UrgeStats(0, false);
        dto.setUrgeCount(stats.count);
        dto.setOpenUrge(stats.open);
        if (withUrgeRecords) {
            List<ClearanceUrgeRecord> records = urgeRecords != null ? urgeRecords
                    : urgeRepository.findByOrderIdOrderByUrgeTimeDescIdDesc(order.getId());
            dto.setUrgeRecords(records.stream().map(this::toUrgeDTO).collect(Collectors.toList()));
        }

        lockerRepository.findById(order.getLockerId()).ifPresent(locker -> {
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

    private ClearanceUrgeRecordDTO toUrgeDTO(ClearanceUrgeRecord record) {
        ClearanceUrgeRecordDTO dto = new ClearanceUrgeRecordDTO();
        dto.setId(record.getId());
        dto.setOrderId(record.getOrderId());
        dto.setUrgeTime(record.getUrgeTime());
        dto.setOperator(record.getOperator());
        dto.setStatus(record.getStatus().name());
        dto.setStatusName(record.getStatus().getDisplayName());
        dto.setCloseNote(record.getCloseNote());
        dto.setCloseOperator(record.getCloseOperator());
        dto.setCloseTime(record.getCloseTime());
        dto.setAutoClosed(record.getAutoClosed());
        dto.setCreateTime(record.getCreateTime());
        return dto;
    }

    /** 单号：QG + 时间戳 + 随机后缀，冲突时重试 */
    private String generateOrderNo() {
        String orderNo;
        do {
            orderNo = "QG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        } while (clearanceOrderRepository.existsByOrderNo(orderNo));
        return orderNo;
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
