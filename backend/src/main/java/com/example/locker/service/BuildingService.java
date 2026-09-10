package com.example.locker.service;

import com.example.locker.dto.BuildingTreeDTO;
import com.example.locker.dto.HierarchyReferenceDTO;
import com.example.locker.dto.UnitDTO;
import com.example.locker.entity.Building;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.repository.ArchiveItemRepository;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BuildingService {


    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private ArchiveItemRepository archiveItemRepository;

    @Cacheable(value = "buildingTree", key = "'tree'")
    public List<BuildingTreeDTO> getBuildingTree() {
        List<Building> buildings = buildingRepository.findAllByOrderBySortOrderAsc();
        List<Unit> units = unitRepository.findAll();

        return buildings.stream().map(building -> {
            BuildingTreeDTO dto = new BuildingTreeDTO();
            dto.setId(building.getId());
            dto.setName(building.getName());
            dto.setCode(building.getCode());

            List<UnitDTO> unitDTOs = units.stream()
                    .filter(unit -> unit.getBuildingId().equals(building.getId()))
                    .map(unit -> {
                        UnitDTO unitDTO = new UnitDTO();
                        unitDTO.setId(unit.getId());
                        unitDTO.setName(unit.getName());
                        unitDTO.setCode(unit.getCode());
                        unitDTO.setBuildingId(unit.getBuildingId());
                        return unitDTO;
                    })
                    .sorted((a, b) -> {
                        int sortA = a.getCode() != null ? 0 : 1;
                        int sortB = b.getCode() != null ? 0 : 1;
                        return sortA - sortB;
                    })
                    .collect(Collectors.toList());

            dto.setChildren(unitDTOs);
            return dto;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "units", key = "#buildingId")
    public List<UnitDTO> getUnitsByBuildingId(Long buildingId) {
        List<Unit> units = unitRepository.findByBuildingIdOrderBySortOrderAsc(buildingId);
        return units.stream().map(unit -> {
            UnitDTO dto = new UnitDTO();
            dto.setId(unit.getId());
            dto.setName(unit.getName());
            dto.setCode(unit.getCode());
            dto.setBuildingId(unit.getBuildingId());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public Building createBuilding(Building building) {
        validateBuilding(building);
        building.setId(null);
        String code = normalizeCode(building.getCode());
        building.setCode(code);
        if (code != null && buildingRepository.existsByCode(code)) {
            throw new IllegalArgumentException("楼栋编码已存在: " + code);
        }
        if (building.getSortOrder() == null) {
            building.setSortOrder(0);
        }
        return buildingRepository.save(building);
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public Building updateBuilding(Long id, Building building) {
        validateBuilding(building);
        Building existing = buildingRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("楼栋不存在: " + id));
        String code = normalizeCode(building.getCode());
        if (code != null && buildingRepository.existsByCodeAndIdNot(code, id)) {
            throw new IllegalArgumentException("楼栋编码已存在: " + code);
        }
        existing.setName(building.getName().trim());
        existing.setCode(code);
        if (building.getSortOrder() != null) {
            existing.setSortOrder(building.getSortOrder());
        }
        return buildingRepository.save(existing);
    }

    /**
     * 删除前统计关联影响：关联快递柜、引用这些快递柜的归档快照、楼栋下单元数量。
     */
    public HierarchyReferenceDTO getBuildingReferences(Long id) {
        Building building = buildingRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("楼栋不存在: " + id));
        List<Locker> lockers = lockerRepository.findByBuildingId(building.getId());
        long lockerCount = lockers.size();
        long archiveCount = countRelatedArchives(lockers);
        long unitCount = unitRepository.findByBuildingIdOrderBySortOrderAsc(building.getId()).size();
        return HierarchyReferenceDTO.of(building.getId(), "BUILDING", lockerCount, archiveCount, unitCount);
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public void deleteBuilding(Long id) {
        HierarchyReferenceDTO ref = getBuildingReferences(id);
        if (ref.getLockerCount() > 0 || ref.getArchiveCount() > 0) {
            throw new IllegalArgumentException(buildDeleteBlockedMessage(ref));
        }
        // 无关联柜体，先显式删除单元，再删除楼栋，避免不同数据库外键级联行为差异
        unitRepository.deleteByBuildingId(id);
        buildingRepository.deleteById(id);
    }

    /**
     * 根据柜体集合统计引用它们的归档快照数量（去重）。
     */
    long countRelatedArchives(List<Locker> lockers) {
        if (lockers == null || lockers.isEmpty()) {
            return 0L;
        }
        List<Long> lockerIds = lockers.stream().map(Locker::getId).collect(Collectors.toList());
        return archiveItemRepository.countDistinctArchiveIdByLockerIdIn(lockerIds);
    }

    static String buildDeleteBlockedMessage(HierarchyReferenceDTO ref) {
        StringBuilder sb = new StringBuilder("存在关联数据，禁止删除：");
        if (ref.getLockerCount() > 0) {
            sb.append("关联快递柜 ").append(ref.getLockerCount()).append(" 台");
        }
        if (ref.getArchiveCount() > 0) {
            if (ref.getLockerCount() > 0) {
                sb.append("，");
            }
            sb.append("归档快照 ").append(ref.getArchiveCount()).append(" 条");
        }
        sb.append("。请先调整或删除关联快递柜后再操作。");
        return sb.toString();
    }

    private void validateBuilding(Building building) {
        if (building == null || !StringUtils.hasText(building.getName())) {
            throw new IllegalArgumentException("楼栋名称不能为空");
        }
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim();
    }

    public Building getBuildingById(Long id) {
        return buildingRepository.findById(id).orElse(null);
    }
}
