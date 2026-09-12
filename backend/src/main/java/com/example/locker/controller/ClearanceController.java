package com.example.locker.controller;

import com.example.locker.dto.*;
import com.example.locker.service.ClearanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clearances")
@CrossOrigin(origins = "*")
public class ClearanceController {

    @Autowired
    private ClearanceService clearanceService;

    /** 登记滞留件清柜单（永久停用柜也可补登历史滞留） */
    @PostMapping
    public ResponseEntity<ClearanceOrderDTO> createOrder(@RequestBody ClearanceOrderCreateRequest request) {
        return ResponseEntity.ok(clearanceService.createOrder(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ClearanceOrderDTO>> getOrders(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            /** 单据状态：PROCESSING-办理中, COMPLETED-已办结 */
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId,
            /** 单号 / 柜体编号 / 滞留格口 关键字 */
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(clearanceService.getOrders(page, size, status, lockerId, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClearanceOrderDTO> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(clearanceService.getOrder(id));
    }

    /** 办结清柜单，处理结果必填 */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ClearanceOrderDTO> completeOrder(
            @PathVariable Long id,
            @RequestBody ClearanceCompleteRequest request) {
        return ResponseEntity.ok(clearanceService.completeOrder(id, request));
    }

    /** 某台柜体的全部清柜单 */
    @GetMapping("/locker/{lockerId}")
    public ResponseEntity<List<ClearanceOrderDTO>> getLockerOrders(@PathVariable Long lockerId) {
        return ResponseEntity.ok(clearanceService.getLockerOrders(lockerId));
    }

    /** 按柜滞留一览：正常柜全量列出，漏登滞留登记的柜体可见 */
    @GetMapping("/locker-overview")
    public ResponseEntity<List<LockerClearanceOverviewDTO>> getLockerOverview() {
        return ResponseEntity.ok(clearanceService.getLockerOverview());
    }

    /** 登记可选柜体（含永久停用柜，用于补登历史滞留） */
    @GetMapping("/locker-options")
    public ResponseEntity<List<ClearanceLockerOptionDTO>> getLockerOptions() {
        return ResponseEntity.ok(clearanceService.getLockerOptions());
    }

    @GetMapping("/statuses")
    public ResponseEntity<Map<String, String>> getStatuses() {
        return ResponseEntity.ok(clearanceService.getStatusMap());
    }
}
