package com.example.locker.controller;

import com.example.locker.dto.*;
import com.example.locker.entity.InspectionIssue;
import com.example.locker.service.InspectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inspections")
@CrossOrigin(origins = "*")
public class InspectionController {

    @Autowired
    private InspectionService inspectionService;

    @PostMapping
    public ResponseEntity<InspectionTaskDTO> createTask(@RequestBody InspectionTaskCreateRequest request) {
        return ResponseEntity.ok(inspectionService.createTask(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<InspectionTaskDTO>> getTasks(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            /** 任务状态；INCOMPLETE 表示未完成（待开始+进行中） */
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean abnormal) {
        return ResponseEntity.ok(inspectionService.getTasks(page, size, status, keyword, overdue, abnormal));
    }

    @GetMapping("/scope")
    public ResponseEntity<InspectionScopeDTO> getScope(
            @RequestParam Long buildingId,
            @RequestParam(required = false) Long unitId) {
        return ResponseEntity.ok(inspectionService.getScope(buildingId, unitId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InspectionTaskDTO> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(inspectionService.getTask(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        inspectionService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/records")
    public ResponseEntity<List<InspectionRecordDTO>> getTaskRecords(@PathVariable Long id) {
        return ResponseEntity.ok(inspectionService.getTaskRecords(id));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<InspectionTaskDTO> submitRecords(
            @PathVariable Long id,
            @RequestBody InspectionSubmitRequest request) {
        return ResponseEntity.ok(inspectionService.submitRecords(id, request));
    }

    @GetMapping("/{id}/issues")
    public ResponseEntity<List<InspectionIssueDTO>> getTaskIssues(
            @PathVariable Long id,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(inspectionService.getTaskIssues(id, status));
    }

    @PutMapping("/issues/{issueId}")
    public ResponseEntity<InspectionIssue> handleIssue(
            @PathVariable Long issueId,
            @RequestBody IssueHandleRequest request) {
        return ResponseEntity.ok(inspectionService.handleIssue(issueId, request));
    }

    @GetMapping("/cycles")
    public ResponseEntity<Map<String, String>> getCycles() {
        return ResponseEntity.ok(inspectionService.getCycleMap());
    }

    @GetMapping("/task-statuses")
    public ResponseEntity<Map<String, String>> getTaskStatuses() {
        return ResponseEntity.ok(inspectionService.getTaskStatusMap());
    }
}
