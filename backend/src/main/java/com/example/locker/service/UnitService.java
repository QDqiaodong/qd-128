package com.example.locker.service;

import com.example.locker.dto.HierarchyReferenceDTO;
import com.example.locker.dto.UnitDTO;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UnitService {

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private BuildingService buildingService;

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public Unit createUnit(Unit unit) {
        validateUnit(unit);
        if (unit.getBuildingId() == null
                || !buildingRepository.existsById(unit.getBuildingId())) {
            throw new IllegalArgumentException("所属楼栋不存在，无法新增单元");
        }
        unit.setId(null);
        String code = normalizeCode(unit.getCode());
        unit.setCode(code);
        if (code != null && unitRepository.existsByBuildingIdAndCode(unit.getBuildingId(), code)) {
            throw new IllegalArgumentException("该楼栋下单元编码已存在: " + code);
        }
        if (unit.getSortOrder() == null) {
            unit.setSortOrder(0);
        }
        return unitRepository.save(unit);
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public Unit updateUnit(Long id, Unit unit) {
        validateUnit(unit);
        Unit existing = unitRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("单元不存在: " + id));
        String code = normalizeCode(unit.getCode());
        if (code != null && unitRepository.existsByBuildingIdAndCodeAndIdNot(
                existing.getBuildingId(), code, id)) {
            throw new IllegalArgumentException("该楼栋下单元编码已存在: " + code);
        }
        existing.setName(unit.getName().trim());
        existing.setCode(code);
        if (unit.getSortOrder() != null) {
            existing.setSortOrder(unit.getSortOrder());
        }
        return unitRepository.save(existing);
    }

    /**
     * 删除前统计关联影响：关联快递柜、引用这些快递柜的归档快照。
     */
    public HierarchyReferenceDTO getUnitReferences(Long id) {
        Unit unit = unitRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("单元不存在: " + id));
        List<Locker> lockers = lockerRepository.findByUnitId(unit.getId());
        long lockerCount = lockers.size();
        long archiveCount = buildingService.countRelatedArchives(lockers);
        return HierarchyReferenceDTO.of(unit.getId(), "UNIT", lockerCount, archiveCount, 0);
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public void deleteUnit(Long id) {
        HierarchyReferenceDTO ref = getUnitReferences(id);
        if (ref.getLockerCount() > 0 || ref.getArchiveCount() > 0) {
            throw new IllegalArgumentException(BuildingService.buildDeleteBlockedMessage(ref));
        }
        unitRepository.deleteById(id);
    }

    public Unit getUnitById(Long id) {
        return unitRepository.findById(id).orElse(null);
    }

    public List<UnitDTO> getUnitsByBuildingIds(List<Long> buildingIds) {
        List<Unit> units = unitRepository.findByBuildingIdInOrderBySortOrderAsc(buildingIds);
        return units.stream().map(unit -> {
            UnitDTO dto = new UnitDTO();
            dto.setId(unit.getId());
            dto.setName(unit.getName());
            dto.setCode(unit.getCode());
            dto.setBuildingId(unit.getBuildingId());
            return dto;
        }).collect(Collectors.toList());
    }

    private void validateUnit(Unit unit) {
        if (unit == null || !StringUtils.hasText(unit.getName())) {
            throw new IllegalArgumentException("单元名称不能为空");
        }
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim();
    }
}
