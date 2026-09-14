package com.example.locker.controller;

import com.example.locker.dto.CollectionSuspensionCreateRequest;
import com.example.locker.dto.CollectionSuspensionRecordDTO;
import com.example.locker.dto.CollectionSuspensionResumeRequest;
import com.example.locker.dto.LockerCollectionSuspensionOverviewDTO;
import com.example.locker.dto.PageResponse;
import com.example.locker.service.CollectionSuspensionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collection-suspensions")
@CrossOrigin(origins = "*")
public class CollectionSuspensionController {

    @Autowired
    private CollectionSuspensionService collectionSuspensionService;

    /** 登记夜间停收转投：开始停收时间、预计恢复时间、值班人必填；同一柜已有停收中记录时后端拦截 */
    @PostMapping
    public ResponseEntity<CollectionSuspensionRecordDTO> register(
            @RequestBody CollectionSuspensionCreateRequest request) {
        return ResponseEntity.ok(collectionSuspensionService.register(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<CollectionSuspensionRecordDTO>> getRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            /** 停收状态：SUSPENDED-停收中, RESUMED-已恢复 */
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId,
            /** 台账编号 / 值班人 / 柜体编号 关键字 */
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(collectionSuspensionService.getRecords(page, size, status, lockerId, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollectionSuspensionRecordDTO> getRecord(@PathVariable Long id) {
        return ResponseEntity.ok(collectionSuspensionService.getRecord(id));
    }

    /** 确认已恢复：同一条记录状态变为已恢复，柜体停收标记随之恢复；已恢复不能重复操作 */
    @PostMapping("/{id}/resume")
    public ResponseEntity<CollectionSuspensionRecordDTO> resume(
            @PathVariable Long id,
            @RequestBody(required = false) CollectionSuspensionResumeRequest request) {
        return ResponseEntity.ok(collectionSuspensionService.resume(id, request));
    }

    /** 某台柜体的全部停收转投记录（柜详情页停收记录） */
    @GetMapping("/locker/{lockerId}")
    public ResponseEntity<List<CollectionSuspensionRecordDTO>> getLockerRecords(@PathVariable Long lockerId) {
        return ResponseEntity.ok(collectionSuspensionService.getLockerRecords(lockerId));
    }

    /** 按停收状态一览：全部柜体列出，停收标记与停收中条数实时推导 */
    @GetMapping("/locker-overview")
    public ResponseEntity<List<LockerCollectionSuspensionOverviewDTO>> getLockerOverview() {
        return ResponseEntity.ok(collectionSuspensionService.getLockerOverview());
    }

    @GetMapping("/statuses")
    public ResponseEntity<Map<String, String>> getStatuses() {
        return ResponseEntity.ok(collectionSuspensionService.getStatusMap());
    }
}
