package com.example.locker.controller;

import com.example.locker.dto.BuildingTreeDTO;
import com.example.locker.dto.HierarchyReferenceDTO;
import com.example.locker.dto.UnitDTO;
import com.example.locker.entity.Building;
import com.example.locker.entity.Unit;
import com.example.locker.service.BuildingService;
import com.example.locker.service.UnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BuildingController {

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private UnitService unitService;

    @GetMapping("/buildings")
    public ResponseEntity<List<BuildingTreeDTO>> getBuildingTree() {
        return ResponseEntity.ok(buildingService.getBuildingTree());
    }

    @GetMapping("/buildings/{id}/units")
    public ResponseEntity<List<UnitDTO>> getUnitsByBuildingId(@PathVariable Long id) {
        return ResponseEntity.ok(buildingService.getUnitsByBuildingId(id));
    }

    @GetMapping("/buildings/{id}/references")
    public ResponseEntity<HierarchyReferenceDTO> getBuildingReferences(@PathVariable Long id) {
        return ResponseEntity.ok(buildingService.getBuildingReferences(id));
    }

    @PostMapping("/buildings")
    public ResponseEntity<Building> createBuilding(@RequestBody Building building) {
        return ResponseEntity.ok(buildingService.createBuilding(building));
    }

    @PutMapping("/buildings/{id}")
    public ResponseEntity<Building> updateBuilding(@PathVariable Long id, @RequestBody Building building) {
        return ResponseEntity.ok(buildingService.updateBuilding(id, building));
    }

    @DeleteMapping("/buildings/{id}")
    public ResponseEntity<Void> deleteBuilding(@PathVariable Long id) {
        buildingService.deleteBuilding(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/units")
    public ResponseEntity<Unit> createUnit(@RequestBody Unit unit) {
        return ResponseEntity.ok(unitService.createUnit(unit));
    }

    @PutMapping("/units/{id}")
    public ResponseEntity<Unit> updateUnit(@PathVariable Long id, @RequestBody Unit unit) {
        return ResponseEntity.ok(unitService.updateUnit(id, unit));
    }

    @DeleteMapping("/units/{id}")
    public ResponseEntity<Void> deleteUnit(@PathVariable Long id) {
        unitService.deleteUnit(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/units/{id}/references")
    public ResponseEntity<HierarchyReferenceDTO> getUnitReferences(@PathVariable Long id) {
        return ResponseEntity.ok(unitService.getUnitReferences(id));
    }
}
