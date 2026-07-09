package com.example.locker.service;

import com.example.locker.dto.*;
import com.example.locker.entity.AdjustmentRecord;
import com.example.locker.entity.Locker;
import com.example.locker.repository.AdjustmentRecordRepository;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.LockerRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        buildingRepository.findById(locker.getBuildingId()).ifPresent(building -> 
                dto.setBuildingName(building.getName()));
        unitRepository.findById(locker.getUnitId()).ifPresent(unit -> 
                dto.setUnitName(unit.getName()));

        return dto;
    }

    public PageResponse<LockerDTO> getLockers(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<Locker> lockerPage = lockerRepository.findAll(pageable);
        List<LockerDTO> dtoList = lockerPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return new PageResponse<>(dtoList, lockerPage.getTotalElements(), page, size);
    }

    public LockerDTO getLockerById(Long id) {
        Locker locker = lockerRepository.findById(id).orElseThrow(() -> 
                new RuntimeException("快递柜不存在: " + id));
        return convertToDTO(locker);
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

    public PageResponse<LockerDTO> filterLockers(FilterRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize(), 
                Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<Locker> spec = (root, query, cb) -> {
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Locker> lockerPage = lockerRepository.findAll(spec, pageable);
        List<LockerDTO> dtoList = lockerPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return new PageResponse<>(dtoList, lockerPage.getTotalElements(), request.getPage(), request.getSize());
    }

    public List<LockerDTO> filterLockersForArchive(FilterRequest request) {
        Specification<Locker> spec = (root, query, cb) -> {
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Locker> lockers = lockerRepository.findAll(spec);
        return lockers.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<AdjustmentRecord> getAdjustmentRecords(Long lockerId) {
        return adjustmentRecordRepository.findByLockerIdOrderByAdjustTimeDesc(lockerId);
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

        return adjustmentRecordRepository.save(record);
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
