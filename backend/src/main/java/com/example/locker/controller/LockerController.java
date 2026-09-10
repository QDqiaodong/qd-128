package com.example.locker.controller;

import com.example.locker.dto.*;
import com.example.locker.entity.AdjustmentRecord;
import com.example.locker.entity.StatusChangeRecord;
import com.example.locker.service.LockerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lockers")
@CrossOrigin(origins = "*")
public class LockerController {

    @Autowired
    private LockerService lockerService;

    @GetMapping
    public ResponseEntity<PageResponse<LockerDTO>> getLockers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) List<String> statuses) {
        return ResponseEntity.ok(lockerService.getLockers(page, size, statuses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LockerDTO> getLockerById(@PathVariable Long id) {
        return ResponseEntity.ok(lockerService.getLockerById(id));
    }

    @PostMapping
    public ResponseEntity<LockerDTO> createLocker(@RequestBody LockerCreateRequest request) {
        return ResponseEntity.ok(lockerService.createLocker(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LockerDTO> updateLocker(@PathVariable Long id, @RequestBody LockerUpdateRequest request) {
        return ResponseEntity.ok(lockerService.updateLocker(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLocker(@PathVariable Long id) {
        lockerService.deleteLocker(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/filter")
    public ResponseEntity<PageResponse<LockerDTO>> filterLockers(@RequestBody FilterRequest request) {
        return ResponseEntity.ok(lockerService.filterLockers(request));
    }

    @PostMapping("/{id}/adjust")
    public ResponseEntity<AdjustmentRecord> adjustLocker(@PathVariable Long id, @RequestBody AdjustRequest request) {
        return ResponseEntity.ok(lockerService.adjustLocker(id, request));
    }

    @GetMapping("/{id}/adjustments")
    public ResponseEntity<List<AdjustmentRecord>> getAdjustmentRecords(@PathVariable Long id) {
        return ResponseEntity.ok(lockerService.getAdjustmentRecords(id));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<StatusChangeRecord> changeLockerStatus(
            @PathVariable Long id,
            @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(lockerService.changeLockerStatus(id, request));
    }

    @GetMapping("/{id}/status-changes")
    public ResponseEntity<List<StatusChangeRecord>> getStatusChangeRecords(
            @PathVariable Long id,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(lockerService.getStatusChangeRecords(id, status));
    }

    @GetMapping("/spec-types")
    public ResponseEntity<Map<String, String>> getSpecTypes() {
        return ResponseEntity.ok(lockerService.getSpecTypeMap());
    }

    @GetMapping("/statuses")
    public ResponseEntity<Map<String, String>> getStatuses() {
        return ResponseEntity.ok(lockerService.getStatusMap());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countLockers() {
        return ResponseEntity.ok(lockerService.countLockers());
    }

    @GetMapping("/count/building/{buildingId}")
    public ResponseEntity<Long> countLockersByBuilding(@PathVariable Long buildingId) {
        return ResponseEntity.ok(lockerService.countLockersByBuilding(buildingId));
    }
}
