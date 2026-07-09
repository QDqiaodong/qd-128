package com.example.locker.service;

import com.example.locker.dto.BuildingTreeDTO;
import com.example.locker.dto.UnitDTO;
import com.example.locker.entity.Building;
import com.example.locker.entity.Unit;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.UnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BuildingService {


    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

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
        return buildingRepository.save(building);
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public Building updateBuilding(Long id, Building building) {
        Building existing = buildingRepository.findById(id).orElseThrow(() -> 
                new RuntimeException("楼栋不存在: " + id));
        existing.setName(building.getName());
        existing.setCode(building.getCode());
        existing.setSortOrder(building.getSortOrder());
        return buildingRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public void deleteBuilding(Long id) {
        buildingRepository.deleteById(id);
    }

    public Building getBuildingById(Long id) {
        return buildingRepository.findById(id).orElse(null);
    }
}
