package com.example.locker.service;

import com.example.locker.dto.LockerRepairOverviewDTO;
import com.example.locker.dto.PageResponse;
import com.example.locker.dto.RepairTicketCompleteRequest;
import com.example.locker.dto.RepairTicketCreateRequest;
import com.example.locker.dto.RepairTicketDTO;
import com.example.locker.entity.Building;
import com.example.locker.entity.Locker;
import com.example.locker.entity.RepairTicket;
import com.example.locker.entity.Unit;
import com.example.locker.enums.LockerStatus;
import com.example.locker.enums.RepairStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.RepairTicketRepository;
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
public class RepairTicketService {

    @Autowired
    private RepairTicketRepository repairTicketRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    // ===================== 报修建单 =====================

    /**
     * 登记格口报修。整个登记在单个事务内一次落库：
     * 故障格口、故障现象、报修人全部校验通过后才保存，任一校验不通过整体回滚，
     * 不会写出半条报修单；登记窗未提交前关闭仅丢弃草稿，不产生任何数据。
     * 同一柜同一格口存在处理中报修单时不允许重复建单。
     */
    @Transactional
    public RepairTicketDTO createTicket(RepairTicketCreateRequest request) {
        if (request == null || request.getLockerId() == null) {
            throw new IllegalArgumentException("请选择报修的柜体");
        }
        Locker locker = lockerRepository.findById(request.getLockerId()).orElseThrow(() ->
                new IllegalArgumentException("快递柜不存在: " + request.getLockerId()));

        if (!StringUtils.hasText(request.getCompartmentNo())) {
            throw new IllegalArgumentException("请填写故障格口");
        }
        if (!StringUtils.hasText(request.getSymptom())) {
            throw new IllegalArgumentException("请填写故障现象");
        }
        if (!StringUtils.hasText(request.getReporter())) {
            throw new IllegalArgumentException("请填写报修人");
        }

        String compartmentNo = request.getCompartmentNo().trim();
        // 纯数字格口编号不能超出该柜格口范围，防止篡改请求报修不存在的格口
        if (locker.getCompartmentCount() != null && compartmentNo.matches("\\d{1,9}")) {
            int no = Integer.parseInt(compartmentNo);
            if (no < 1 || no > locker.getCompartmentCount()) {
                throw new IllegalArgumentException(
                        "故障格口超出该柜格口范围（1-" + locker.getCompartmentCount() + "）");
            }
        }
        if (repairTicketRepository.existsByLockerIdAndCompartmentNoAndStatus(
                locker.getId(), compartmentNo, RepairStatus.PROCESSING)) {
            throw new IllegalArgumentException("该格口已有处理中的报修单，请勿重复报修");
        }

        RepairTicket ticket = new RepairTicket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setLockerId(locker.getId());
        ticket.setCompartmentNo(compartmentNo);
        ticket.setSymptom(request.getSymptom().trim());
        ticket.setReporter(request.getReporter().trim());
        ticket.setStatus(RepairStatus.PROCESSING);
        ticket.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);

        return toDTO(repairTicketRepository.save(ticket));
    }

    // ===================== 完工 =====================

    /**
     * 完工：必须填写处理人和处理结果，状态由处理中变为已修好并记录完工时间。
     * 已修好的单不能重复完工。整个完工在单个事务内一次落库，任一校验不通过整体回滚。
     */
    @Transactional
    public RepairTicketDTO completeTicket(Long id, RepairTicketCompleteRequest request) {
        if (request == null || !StringUtils.hasText(request.getHandler())) {
            throw new IllegalArgumentException("完工必须填写处理人");
        }
        if (!StringUtils.hasText(request.getRepairResult())) {
            throw new IllegalArgumentException("完工必须填写处理结果");
        }
        RepairTicket ticket = repairTicketRepository.findById(id).orElseThrow(() ->
                new RuntimeException("报修单不存在: " + id));
        if (ticket.getStatus() == RepairStatus.FIXED) {
            throw new IllegalArgumentException("该报修单已修好，请勿重复完工");
        }

        ticket.setStatus(RepairStatus.FIXED);
        ticket.setHandler(request.getHandler().trim());
        ticket.setRepairResult(request.getRepairResult().trim());
        ticket.setFixedTime(LocalDateTime.now());
        return toDTO(repairTicketRepository.save(ticket));
    }

    // ===================== 查询 =====================

    /**
     * 报修台账分页列表，可按状态（处理中/已修好）、柜体、单号/格口/报修人/柜体编号关键字过滤。
     */
    public PageResponse<RepairTicketDTO> getTickets(Integer page, Integer size, String status,
                                                    Long lockerId, String keyword) {
        Specification<RepairTicket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), RepairStatus.fromCode(status.trim())));
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
                        cb.like(root.get("ticketNo"), like),
                        cb.like(root.get("compartmentNo"), like),
                        cb.like(root.get("symptom"), like),
                        cb.like(root.get("reporter"), like),
                        root.get("lockerId").in(sub)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RepairTicket> ticketPage = repairTicketRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<RepairTicketDTO> list = ticketPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return new PageResponse<>(list, ticketPage.getTotalElements(), page, size);
    }

    public RepairTicketDTO getTicket(Long id) {
        RepairTicket ticket = repairTicketRepository.findById(id).orElseThrow(() ->
                new RuntimeException("报修单不存在: " + id));
        return toDTO(ticket);
    }

    /**
     * 某台柜体的全部报修单（柜详情页留存历史）。
     */
    public List<RepairTicketDTO> getLockerTickets(Long lockerId) {
        lockerRepository.findById(lockerId).orElseThrow(() ->
                new RuntimeException("快递柜不存在: " + lockerId));
        return repairTicketRepository.findByLockerIdOrderByCreateTimeDesc(lockerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按柜报修状态一览：全部柜体（含停用柜）列出，维修中的排前面。
     * 可用标记与处理中条数实时由报修台账推导，台账是唯一数据源，
     * 保证刷新后与柜体列表、详情的标记和报修条数保持一致。
     */
    public List<LockerRepairOverviewDTO> getLockerOverview() {
        List<Locker> lockers = lockerRepository.findAll(Sort.by(Sort.Direction.ASC, "lockerNo"));
        if (lockers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        Map<Long, List<RepairTicket>> byLocker = repairTicketRepository.findByLockerIdIn(lockerIds)
                .stream().collect(Collectors.groupingBy(RepairTicket::getLockerId));

        Map<Long, String> buildingNames = loadBuildingNames(lockers);
        Map<Long, String> unitNames = loadUnitNames(lockers);

        List<LockerRepairOverviewDTO> result = new ArrayList<>();
        for (Locker locker : lockers) {
            List<RepairTicket> tickets = byLocker.getOrDefault(locker.getId(), Collections.emptyList());
            List<RepairTicket> open = tickets.stream()
                    .filter(t -> t.getStatus() == RepairStatus.PROCESSING)
                    .collect(Collectors.toList());

            LockerRepairOverviewDTO dto = new LockerRepairOverviewDTO();
            dto.setLockerId(locker.getId());
            dto.setLockerNo(locker.getLockerNo());
            dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
            dto.setUnitName(unitNames.get(locker.getUnitId()));
            dto.setFloor(locker.getFloor());
            LockerStatus status = locker.getStatus() == null ? LockerStatus.ACTIVE : locker.getStatus();
            dto.setStatus(status.name());
            dto.setStatusName(status.getDisplayName());
            dto.setRepairing(!open.isEmpty());
            dto.setOpenTicketCount(open.size());
            dto.setTotalTicketCount(tickets.size());
            dto.setLastReportTime(tickets.stream()
                    .map(RepairTicket::getCreateTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            result.add(dto);
        }

        // 维修中的排前面，便于优先跟进完工；其余按柜体编号排序
        result.sort(Comparator.comparing(LockerRepairOverviewDTO::getRepairing).reversed()
                .thenComparing(LockerRepairOverviewDTO::getLockerNo));
        return result;
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (RepairStatus status : RepairStatus.values()) {
            map.put(status.name(), status.getDisplayName());
        }
        return map;
    }

    // ===================== 转换与辅助 =====================

    private RepairTicketDTO toDTO(RepairTicket ticket) {
        RepairTicketDTO dto = new RepairTicketDTO();
        dto.setId(ticket.getId());
        dto.setTicketNo(ticket.getTicketNo());
        dto.setLockerId(ticket.getLockerId());
        dto.setCompartmentNo(ticket.getCompartmentNo());
        dto.setSymptom(ticket.getSymptom());
        dto.setReporter(ticket.getReporter());
        dto.setStatus(ticket.getStatus());
        dto.setStatusName(ticket.getStatus() == null ? null : ticket.getStatus().getDisplayName());
        // 处理中 = 尚未完工，列表与详情统一据此标记
        dto.setProcessing(ticket.getStatus() == RepairStatus.PROCESSING);
        dto.setHandler(ticket.getHandler());
        dto.setRepairResult(ticket.getRepairResult());
        dto.setFixedTime(ticket.getFixedTime());
        dto.setRemark(ticket.getRemark());
        dto.setCreateTime(ticket.getCreateTime());
        dto.setUpdateTime(ticket.getUpdateTime());

        lockerRepository.findById(ticket.getLockerId()).ifPresent(locker -> {
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

    /** 报修单号：BX + 时间戳 + 随机后缀，冲突时重试 */
    private String generateTicketNo() {
        String ticketNo;
        do {
            ticketNo = "BX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        } while (repairTicketRepository.existsByTicketNo(ticketNo));
        return ticketNo;
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
