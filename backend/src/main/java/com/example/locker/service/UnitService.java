package com.example.locker.service;

import com.example.locker.dto.UnitDTO;
import com.example.locker.entity.Unit;
import com.example.locker.repository.UnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UnitService {

    @Autowired
    private UnitRepository unitRepository;

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public Unit createUnit(Unit unit) {
        return unitRepository.save(unit);
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public Unit updateUnit(Long id, Unit unit) {
        Unit existing = unitRepository.findById(id).orElseThrow(() -> 
                new RuntimeException("单元不存在: " + id));
        existing.setName(unit.getName());
        existing.setCode(unit.getCode());
        existing.setSortOrder(unit.getSortOrder());
        return unitRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = {"buildingTree", "units"}, allEntries = true)
    public void deleteUnit(Long id) {
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
}
