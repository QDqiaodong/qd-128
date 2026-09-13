package com.example.locker.service;

import com.example.locker.dto.*;
import com.example.locker.entity.*;
import com.example.locker.enums.*;
import com.example.locker.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InspectionService {

    @Autowired
    private InspectionTaskRepository taskRepository;

    @Autowired
    private InspectionRecordRepository recordRepository;

    @Autowired
    private InspectionIssueRepository issueRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private SpecTemplateService specTemplateService;

    @Autowired
    private InspectionReassignRecordRepository reassignRepository;

    @Autowired
    private InspectionUrgeRecordRepository urgeRepository;

    // ===================== 任务 =====================

    @Transactional
    public InspectionTaskDTO createTask(InspectionTaskCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getTaskName())) {
            throw new IllegalArgumentException("请填写巡检任务名称");
        }
        if (request.getBuildingId() == null) {
            throw new IllegalArgumentException("请选择巡检楼栋");
        }
        Building building = buildingRepository.findById(request.getBuildingId()).orElseThrow(() ->
                new IllegalArgumentException("楼栋不存在: " + request.getBuildingId()));

        Unit unit = null;
        if (request.getUnitId() != null) {
            unit = unitRepository.findById(request.getUnitId()).orElseThrow(() ->
                    new IllegalArgumentException("单元不存在: " + request.getUnitId()));
            if (!unit.getBuildingId().equals(building.getId())) {
                throw new IllegalArgumentException("所选单元不属于该楼栋");
            }
        }

        InspectionCycle cycle;
        try {
            cycle = InspectionCycle.fromCode(
                    StringUtils.hasText(request.getCycle()) ? request.getCycle().trim() : InspectionCycle.ONCE.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        List<Locker> lockers = lockersInScope(building, unit, request.getLockerIds());
        if (lockers.isEmpty()) {
            throw new IllegalArgumentException("所选楼栋/单元下暂无可巡检的正常快递柜，无法发起巡检任务");
        }

        InspectionTask task = new InspectionTask();
        task.setTaskName(request.getTaskName().trim());
        task.setBuildingId(building.getId());
        task.setUnitId(unit == null ? null : unit.getId());
        task.setCycle(cycle);
        task.setAssignee(StringUtils.hasText(request.getAssignee()) ? request.getAssignee().trim() : null);
        task.setDeadline(request.getDeadline());
        task.setCreator(StringUtils.hasText(request.getCreator()) ? request.getCreator().trim() : null);
        task.setStatus(InspectionTaskStatus.PENDING);
        task.setTotalLockers(lockers.size());
        task.setCompletedLockers(0);
        task.setAbnormalCount(0);
        task.setPendingIssueCount(0);

        InspectionTask saved = taskRepository.save(task);

        // 逐台生成巡检明细，柜体与任务的关联关系在发起时冻结
        for (Locker locker : lockers) {
            InspectionRecord record = new InspectionRecord();
            record.setTaskId(saved.getId());
            record.setLockerId(locker.getId());
            recordRepository.save(record);
        }

        return toTaskDTO(saved);
    }

    /**
     * 实时计算发起巡检时的可选柜体范围；停用柜档案仍可在柜体档案中查看，但不进入巡检范围。
     */
    public InspectionScopeDTO getScope(Long buildingId, Long unitId) {
        if (buildingId == null) {
            throw new IllegalArgumentException("请选择巡检楼栋");
        }
        Building building = buildingRepository.findById(buildingId).orElseThrow(() ->
                new IllegalArgumentException("楼栋不存在: " + buildingId));
        Unit unit = null;
        if (unitId != null) {
            unit = unitRepository.findById(unitId).orElseThrow(() ->
                    new IllegalArgumentException("单元不存在: " + unitId));
            if (!unit.getBuildingId().equals(building.getId())) {
                throw new IllegalArgumentException("所选单元不属于该楼栋");
            }
        }

        List<Locker> lockers = lockersInScope(building, unit, null);
        InspectionScopeDTO dto = new InspectionScopeDTO();
        dto.setBuildingId(building.getId());
        dto.setBuildingName(building.getName());
        dto.setUnitId(unit == null ? null : unit.getId());
        dto.setUnitName(unit == null ? null : unit.getName());
        dto.setTotalLockers(lockers.size());
        dto.setLockers(lockers.stream().map(this::toLockerOption).collect(Collectors.toList()));
        return dto;
    }

    public PageResponse<InspectionTaskDTO> getTasks(Integer page, Integer size, String status,
                                                    String keyword, Boolean overdue, Boolean abnormal) {
        return getTasks(page, size, status, keyword, overdue, abnormal, null, null);
    }

    public PageResponse<InspectionTaskDTO> getTasks(Integer page, Integer size, String status,
                                                    String keyword, Boolean overdue, Boolean abnormal,
                                                    String assignee, Boolean urged) {
        Specification<InspectionTask> spec = buildTaskSpec(status, keyword, overdue, abnormal, assignee, urged);
        Page<InspectionTask> taskPage = taskRepository.findAll(spec,
                PageRequest.of(page - 1, size, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createTime")));

        List<InspectionTask> tasks = taskPage.getContent();
        Map<Long, Integer> urgeCountMap = loadUrgeCounts(
                tasks.stream().map(InspectionTask::getId).collect(Collectors.toList()));
        List<InspectionTaskDTO> list = tasks.stream()
                .map(t -> toTaskDTO(t, urgeCountMap.getOrDefault(t.getId(), 0)))
                .collect(Collectors.toList());
        return new PageResponse<>(list, taskPage.getTotalElements(), page, size);
    }

    private Specification<InspectionTask> buildTaskSpec(String status, String keyword,
                                                        Boolean overdue, Boolean abnormal,
                                                        String assignee, Boolean urged) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(status)) {
                if ("INCOMPLETE".equals(status.trim())) {
                    // 未完成 = 待开始 + 进行中
                    predicates.add(root.get("status").in(
                            EnumSet.of(InspectionTaskStatus.PENDING, InspectionTaskStatus.IN_PROGRESS)));
                } else {
                    InspectionTaskStatus parsed = InspectionTaskStatus.fromCode(status.trim());
                    predicates.add(cb.equal(root.get("status"), parsed));
                }
            }

            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("taskName"), like),
                        cb.like(root.get("assignee"), like)));
            }

            // 按（当前）负责人精确筛选，转派后以最新负责人为准
            if (StringUtils.hasText(assignee)) {
                predicates.add(cb.equal(root.get("assignee"), assignee.trim()));
            }

            if (Boolean.TRUE.equals(abnormal)) {
                predicates.add(cb.greaterThan(root.get("pendingIssueCount"), 0));
            }

            if (Boolean.TRUE.equals(overdue)) {
                // 逾期 = 截止时间已过且尚未完成
                predicates.add(cb.and(
                        cb.isNotNull(root.get("deadline")),
                        cb.lessThan(root.get("deadline"), LocalDateTime.now()),
                        cb.notEqual(root.get("status"), InspectionTaskStatus.COMPLETED)));
            }

            if (urged != null) {
                jakarta.persistence.criteria.Subquery<Long> sub = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<InspectionUrgeRecord> urgeRoot =
                        sub.from(InspectionUrgeRecord.class);
                sub.select(urgeRoot.get("id"))
                        .where(cb.equal(urgeRoot.get("taskId"), root.get("id")));
                Predicate exists = cb.exists(sub);
                predicates.add(urged ? exists : cb.not(exists));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public InspectionTask getTaskEntity(Long id) {
        return taskRepository.findById(id).orElseThrow(() ->
                new RuntimeException("巡检任务不存在: " + id));
    }

    public InspectionTaskDTO getTask(Long id) {
        return toTaskDTO(getTaskEntity(id));
    }

    @Transactional
    public void deleteTask(Long id) {
        getTaskEntity(id);
        // 先删催办/转派台账与异常记录，再删明细，最后删任务，避免外键依赖顺序问题
        urgeRepository.deleteAllByTaskId(id);
        reassignRepository.deleteAllByTaskId(id);
        issueRepository.deleteAllInBatch(issueRepository.findByTaskIdOrderByCreateTimeDesc(id));
        recordRepository.deleteAllInBatch(recordRepository.findByTaskIdOrderByIdAsc(id));
        taskRepository.deleteById(id);
    }

    // ===================== 巡检明细（逐台填报） =====================

    /**
     * 任务详情：逐台柜体的检查项结果，以及关联柜体的实时层级信息。
     *
     * @param inspectionStatus 柜级巡检状态筛选：INSPECTED-已巡 / UNINSPECTED-未巡，为空返回全部。
     *                         已巡/未巡以检查项是否填报为唯一口径，与列表筛选保持同源。
     */
    public List<InspectionRecordDTO> getTaskRecords(Long taskId, String inspectionStatus) {
        getTaskEntity(taskId);
        List<InspectionRecord> records = recordRepository.findByTaskIdOrderByIdAsc(taskId);

        List<Long> lockerIds = records.stream().map(InspectionRecord::getLockerId).collect(Collectors.toList());
        Map<Long, Locker> lockerMap = lockerRepository.findAllById(lockerIds).stream()
                .collect(Collectors.toMap(Locker::getId, l -> l));
        Map<Long, String> buildingNames = loadBuildingNames(lockerMap.values());
        Map<Long, String> unitNames = loadUnitNames(lockerMap.values());

        // 预取每个柜体的异常数量，避免 N+1
        Map<Long, Long> pendingByLocker = new HashMap<>();
        Map<Long, Long> totalByLocker = new HashMap<>();
        for (InspectionIssue issue : issueRepository.findByTaskIdOrderByCreateTimeDesc(taskId)) {
            totalByLocker.merge(issue.getLockerId(), 1L, Long::sum);
            if (issue.getStatus() != IssueStatus.RESOLVED) {
                pendingByLocker.merge(issue.getLockerId(), 1L, Long::sum);
            }
        }

        boolean onlyInspected = "INSPECTED".equalsIgnoreCase(StringUtils.trimWhitespace(inspectionStatus));
        boolean onlyUninspected = "UNINSPECTED".equalsIgnoreCase(StringUtils.trimWhitespace(inspectionStatus));

        List<InspectionRecordDTO> result = new ArrayList<>();
        for (InspectionRecord record : records) {
            boolean filled = record.getCompartmentResult() != null
                    || record.getScreenResult() != null
                    || record.getLockResult() != null;
            if ((onlyInspected && !filled) || (onlyUninspected && filled)) {
                continue;
            }

            InspectionRecordDTO dto = new InspectionRecordDTO();
            dto.setId(record.getId());
            dto.setTaskId(taskId);
            dto.setLockerId(record.getLockerId());
            dto.setCompartmentResult(record.getCompartmentResult());
            dto.setScreenResult(record.getScreenResult());
            dto.setLockResult(record.getLockResult());
            dto.setRemark(record.getRemark());
            dto.setInspector(record.getInspector());
            dto.setInspectTime(record.getInspectTime());
            dto.setInspectionStatus(filled ? "INSPECTED" : "UNINSPECTED");
            dto.setPendingIssueCount(pendingByLocker.getOrDefault(record.getLockerId(), 0L).intValue());
            dto.setTotalIssueCount(totalByLocker.getOrDefault(record.getLockerId(), 0L).intValue());

            Locker locker = lockerMap.get(record.getLockerId());
            if (locker != null) {
                dto.setLockerExists(true);
                dto.setLockerNo(locker.getLockerNo());
                dto.setSpecTypeName(specTemplateService.getSpecTypeName(locker.getSpecType()));
                dto.setCompartmentCount(locker.getCompartmentCount());
                dto.setBuildingName(buildingNames.get(locker.getBuildingId()));
                dto.setUnitName(unitNames.get(locker.getUnitId()));
                dto.setFloor(locker.getFloor());
            } else {
                // 柜体被删除时保留关联与已填结果，仅层级信息不可用
                dto.setLockerExists(false);
                dto.setLockerNo("已删除柜体#" + record.getLockerId());
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 提交逐台检查结果。异常项自动生成待处理记录；重新提交时同步异常记录状态，
     * 并刷新任务进度、异常数与任务状态，保证刷新后数据一致。
     */
    @Transactional
    public InspectionTaskDTO submitRecords(Long taskId, InspectionSubmitRequest request) {
        InspectionTask task = getTaskEntity(taskId);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("请至少提交一台柜体的检查结果");
        }

        boolean anyFilled = false;
        for (RecordSubmitItem item : request.getItems()) {
            if (item.getLockerId() == null) {
                continue;
            }
            InspectionRecord record = recordRepository.findByTaskIdAndLockerId(taskId, item.getLockerId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "柜体 " + item.getLockerId() + " 不属于当前巡检任务"));

            CheckResult compartment = parseResult(item.getCompartmentResult());
            CheckResult screen = parseResult(item.getScreenResult());
            CheckResult lock = parseResult(item.getLockResult());

            boolean filled = compartment != null || screen != null || lock != null;
            if (!filled) {
                // 空提交不能落库：否则会把已巡记录抹回未巡，并错误刷新巡检时间
                throw new IllegalArgumentException("请至少完成一个检查项（格口/屏幕/门锁）");
            }
            anyFilled = true;

            record.setCompartmentResult(compartment);
            record.setScreenResult(screen);
            record.setLockResult(lock);
            record.setRemark(StringUtils.hasText(item.getRemark()) ? item.getRemark().trim() : null);
            record.setInspector(StringUtils.hasText(item.getInspector()) ? item.getInspector().trim() : null);
            // 巡检时间只在完成填报时写入，是该柜「已巡」的唯一时间依据
            record.setInspectTime(LocalDateTime.now());
            recordRepository.save(record);

            syncIssues(taskId, record, compartment, screen, lock);
        }

        if (!anyFilled) {
            throw new IllegalArgumentException("请至少完成一个检查项（格口/屏幕/门锁）");
        }

        recalculateTask(task);
        return toTaskDTO(taskRepository.save(task));
    }

    /**
     * 依据本次检查结果同步异常待处理记录：
     * - 新出现的异常项生成待处理记录；
     * - 上次异常、本次恢复正常或标记为不适用的检查项，其仍在「待处理」的记录自动标记已解决；
     * - 已在处理中/已解决的记录保留处理痕迹，不被覆盖。
     */
    private void syncIssues(Long taskId, InspectionRecord record,
                           CheckResult compartment, CheckResult screen, CheckResult lock) {
        Map<InspectionCheckItem, CheckResult> current = new EnumMap<>(InspectionCheckItem.class);
        current.put(InspectionCheckItem.COMPARTMENT, compartment);
        current.put(InspectionCheckItem.SCREEN, screen);
        current.put(InspectionCheckItem.LOCK, lock);

        List<InspectionIssue> existing = issueRepository.findByRecordId(record.getId());
        Map<InspectionCheckItem, List<InspectionIssue>> byItem = new EnumMap<>(InspectionCheckItem.class);
        for (InspectionIssue issue : existing) {
            InspectionCheckItem item = InspectionCheckItem.fromCode(issue.getCheckItem());
            byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(issue);
        }

        for (Map.Entry<InspectionCheckItem, CheckResult> entry : current.entrySet()) {
            InspectionCheckItem checkItem = entry.getKey();
            CheckResult result = entry.getValue();
            List<InspectionIssue> itemIssues = byItem.getOrDefault(checkItem, Collections.emptyList());

            if (result == CheckResult.ABNORMAL) {
                boolean hasOpen = itemIssues.stream()
                        .anyMatch(i -> i.getStatus() != IssueStatus.RESOLVED);
                if (!hasOpen) {
                    InspectionIssue issue = new InspectionIssue();
                    issue.setTaskId(taskId);
                    issue.setRecordId(record.getId());
                    issue.setLockerId(record.getLockerId());
                    issue.setCheckItem(checkItem.getCode());
                    issue.setDescription(checkItem.getDisplayName() + "检查异常");
                    issue.setStatus(IssueStatus.PENDING);
                    issueRepository.save(issue);
                }
            } else if (result == CheckResult.NORMAL || result == CheckResult.NOT_APPLICABLE) {
                // 复检恢复正常或标记不适用：自动关闭仍待处理的记录，处理中及已解决的保留
                String autoNote = result == CheckResult.NORMAL
                        ? "复检恢复正常，自动关闭"
                        : "复检标记为不适用，自动关闭";
                for (InspectionIssue issue : itemIssues) {
                    if (issue.getStatus() == IssueStatus.PENDING) {
                        issue.setStatus(IssueStatus.RESOLVED);
                        issue.setHandleNote(StringUtils.hasText(issue.getHandleNote())
                                ? issue.getHandleNote() : autoNote);
                        issue.setHandleTime(LocalDateTime.now());
                        issueRepository.save(issue);
                    }
                }
            }
        }
    }

    /**
     * 依据明细与异常记录重算任务进度、异常柜体数、待处理异常数和任务状态。
     * 所有统计都以明细/异常表为唯一数据源，保证刷新后进度与异常数一致。
     */
    private void recalculateTask(InspectionTask task) {
        List<InspectionRecord> records = recordRepository.findByTaskIdOrderByIdAsc(task.getId());
        int completed = 0;
        Set<Long> abnormalLockers = new HashSet<>();

        for (InspectionRecord record : records) {
            boolean filled = record.getCompartmentResult() != null
                    || record.getScreenResult() != null
                    || record.getLockResult() != null;
            if (filled) {
                completed++;
            }
            if (record.getCompartmentResult() == CheckResult.ABNORMAL
                    || record.getScreenResult() == CheckResult.ABNORMAL
                    || record.getLockResult() == CheckResult.ABNORMAL) {
                abnormalLockers.add(record.getLockerId());
            }
        }

        List<InspectionIssue> issues = issueRepository.findByTaskIdOrderByCreateTimeDesc(task.getId());
        int pending = (int) issues.stream().filter(i -> i.getStatus() != IssueStatus.RESOLVED).count();

        task.setCompletedLockers(completed);
        task.setAbnormalCount(abnormalLockers.size());
        task.setPendingIssueCount(pending);

        if (completed == 0) {
            task.setStatus(InspectionTaskStatus.PENDING);
        } else if (completed >= task.getTotalLockers()) {
            task.setStatus(InspectionTaskStatus.COMPLETED);
        } else {
            task.setStatus(InspectionTaskStatus.IN_PROGRESS);
        }
    }

    // ===================== 异常待处理记录 =====================

    public List<InspectionIssueDTO> getTaskIssues(Long taskId, String status) {
        getTaskEntity(taskId);
        List<InspectionIssue> issues;
        if (StringUtils.hasText(status)) {
            issues = issueRepository.findByTaskIdAndStatusOrderByCreateTimeDesc(
                    taskId, IssueStatus.fromCode(status.trim()));
        } else {
            issues = issueRepository.findByTaskIdOrderByCreateTimeDesc(taskId);
        }
        return issues.stream().map(this::toIssueDTO).collect(Collectors.toList());
    }

    @Transactional
    public InspectionIssue handleIssue(Long issueId, IssueHandleRequest request) {
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new IllegalArgumentException("请选择处理状态");
        }
        IssueStatus target;
        try {
            target = IssueStatus.fromCode(request.getStatus().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        if (target == IssueStatus.PENDING) {
            throw new IllegalArgumentException("不能将异常记录更新为待处理状态");
        }

        InspectionIssue issue = issueRepository.findById(issueId).orElseThrow(() ->
                new RuntimeException("异常记录不存在: " + issueId));
        issue.setStatus(target);
        issue.setHandler(StringUtils.hasText(request.getHandler()) ? request.getHandler().trim() : null);
        issue.setHandleNote(StringUtils.hasText(request.getHandleNote()) ? request.getHandleNote().trim() : null);
        issue.setHandleTime(LocalDateTime.now());
        InspectionIssue saved = issueRepository.save(issue);

        // 处理状态变化会影响任务的待处理异常数（待处理 + 处理中）
        InspectionTask task = getTaskEntity(issue.getTaskId());
        int pending = (int) issueRepository.findByTaskIdOrderByCreateTimeDesc(task.getId()).stream()
                .filter(i -> i.getStatus() != IssueStatus.RESOLVED)
                .count();
        task.setPendingIssueCount(pending);
        taskRepository.save(task);

        return saved;
    }

    // ===================== 枚举字典 =====================

    public Map<String, String> getCycleMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (InspectionCycle cycle : InspectionCycle.values()) {
            map.put(cycle.name(), cycle.getDisplayName());
        }
        return map;
    }

    public Map<String, String> getTaskStatusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (InspectionTaskStatus status : InspectionTaskStatus.values()) {
            map.put(status.name(), status.getDisplayName());
        }
        return map;
    }

    // ===================== 转换与辅助 =====================

    private Map<Long, Integer> loadUrgeCounts(List<Long> taskIds) {
        Map<Long, Integer> counts = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return counts;
        }
        for (InspectionUrgeRecord record : urgeRepository.findByTaskIdIn(taskIds)) {
            counts.merge(record.getTaskId(), 1, Integer::sum);
        }
        return counts;
    }

    private InspectionTaskDTO toTaskDTO(InspectionTask task, int urgeCount) {
        InspectionTaskDTO dto = toTaskDTO(task);
        dto.setUrgeCount(urgeCount);
        return dto;
    }

    private InspectionTaskDTO toTaskDTO(InspectionTask task) {
        InspectionTaskDTO dto = new InspectionTaskDTO();
        dto.setId(task.getId());
        dto.setTaskName(task.getTaskName());
        dto.setBuildingId(task.getBuildingId());
        dto.setUnitId(task.getUnitId());
        dto.setCycle(task.getCycle());
        dto.setCycleName(task.getCycle() == null ? null : task.getCycle().getDisplayName());
        dto.setAssignee(task.getAssignee());
        dto.setDeadline(task.getDeadline());
        dto.setStatus(task.getStatus());
        dto.setStatusName(task.getStatus() == null ? null : task.getStatus().getDisplayName());
        dto.setTotalLockers(task.getTotalLockers());
        dto.setCompletedLockers(task.getCompletedLockers());
        dto.setAbnormalCount(task.getAbnormalCount());
        dto.setPendingIssueCount(task.getPendingIssueCount());
        dto.setTotalIssueCount(issueRepository.findByTaskIdOrderByCreateTimeDesc(task.getId()).size());
        dto.setCreator(task.getCreator());
        dto.setCreateTime(task.getCreateTime());
        dto.setUpdateTime(task.getUpdateTime());

        buildingRepository.findById(task.getBuildingId())
                .ifPresent(b -> dto.setBuildingName(b.getName()));
        if (task.getUnitId() != null) {
            unitRepository.findById(task.getUnitId())
                    .ifPresent(u -> dto.setUnitName(u.getName()));
        }

        int total = task.getTotalLockers() == null ? 0 : task.getTotalLockers();
        int completed = task.getCompletedLockers() == null ? 0 : task.getCompletedLockers();
        dto.setProgress(total == 0 ? 0 : (int) Math.round(completed * 100.0 / total));
        dto.setOverdue(task.getDeadline() != null
                && task.getStatus() != InspectionTaskStatus.COMPLETED
                && task.getDeadline().isBefore(LocalDateTime.now()));
        return dto;
    }

    private InspectionIssueDTO toIssueDTO(InspectionIssue issue) {
        InspectionIssueDTO dto = new InspectionIssueDTO();
        dto.setId(issue.getId());
        dto.setTaskId(issue.getTaskId());
        dto.setRecordId(issue.getRecordId());
        dto.setLockerId(issue.getLockerId());
        dto.setCheckItem(issue.getCheckItem());
        InspectionCheckItem item = InspectionCheckItem.fromCode(issue.getCheckItem());
        dto.setCheckItemName(item.getDisplayName());
        dto.setDescription(issue.getDescription());
        dto.setStatus(issue.getStatus());
        dto.setStatusName(issue.getStatus() == null ? null : issue.getStatus().getDisplayName());
        dto.setHandler(issue.getHandler());
        dto.setHandleNote(issue.getHandleNote());
        dto.setCreateTime(issue.getCreateTime());
        dto.setHandleTime(issue.getHandleTime());

        lockerRepository.findById(issue.getLockerId()).ifPresent(locker -> {
            dto.setLockerNo(locker.getLockerNo());
            buildingRepository.findById(locker.getBuildingId())
                    .ifPresent(b -> dto.setBuildingName(b.getName()));
            unitRepository.findById(locker.getUnitId())
                    .ifPresent(u -> dto.setUnitName(u.getName()));
        });
        return dto;
    }

    /**
     * 巡检范围只冻结发起时刻状态为 ACTIVE 的柜体。调用方即使强行传入停用柜 ID，
     * 也会在范围、状态两道条件中被过滤，不能生成巡检明细。
     */
    private List<Locker> lockersInScope(Building building, Unit unit, List<Long> lockerIds) {
        List<Locker> lockers = (unit == null
                ? lockerRepository.findByBuildingIdAndStatus(building.getId(), LockerStatus.ACTIVE)
                : lockerRepository.findByUnitIdAndStatus(unit.getId(), LockerStatus.ACTIVE)).stream()
                .filter(locker -> locker.getStatus() == LockerStatus.ACTIVE)
                .collect(Collectors.toList());

        if (lockerIds == null || lockerIds.isEmpty()) {
            return lockers;
        }

        Set<Long> requestedIds = lockerIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (requestedIds.isEmpty()) {
            return lockers;
        }
        return lockers.stream()
                .filter(locker -> requestedIds.contains(locker.getId()))
                .collect(Collectors.toList());
    }

    private InspectionLockerOptionDTO toLockerOption(Locker locker) {
        InspectionLockerOptionDTO dto = new InspectionLockerOptionDTO();
        dto.setId(locker.getId());
        dto.setLockerNo(locker.getLockerNo());
        dto.setCompartmentCount(locker.getCompartmentCount());
        dto.setSpecType(locker.getSpecType());
        dto.setSpecTypeName(specTemplateService.getSpecTypeName(locker.getSpecType()));
        dto.setBuildingId(locker.getBuildingId());
        dto.setUnitId(locker.getUnitId());
        dto.setFloor(locker.getFloor());
        buildingRepository.findById(locker.getBuildingId())
                .ifPresent(building -> dto.setBuildingName(building.getName()));
        unitRepository.findById(locker.getUnitId())
                .ifPresent(unit -> dto.setUnitName(unit.getName()));
        return dto;
    }

    private CheckResult parseResult(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return CheckResult.fromCode(code.trim());
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
