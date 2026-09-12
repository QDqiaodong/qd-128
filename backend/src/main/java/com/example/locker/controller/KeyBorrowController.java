package com.example.locker.controller;

import com.example.locker.dto.*;
import com.example.locker.service.KeyBorrowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/key-borrows")
@CrossOrigin(origins = "*")
public class KeyBorrowController {

    @Autowired
    private KeyBorrowService keyBorrowService;

    /** 登记钥匙借用（永久停用柜也可补登历史借用；同一柜未还清前不能再借出） */
    @PostMapping
    public ResponseEntity<KeyBorrowRecordDTO> createRecord(@RequestBody KeyBorrowCreateRequest request) {
        return ResponseEntity.ok(keyBorrowService.createRecord(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<KeyBorrowRecordDTO>> getRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            /** 借用状态：ON_LOAN-借用中, RETURNED-已归还 */
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId,
            /** 单号 / 借出人 / 事由 / 柜体编号 关键字 */
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(keyBorrowService.getRecords(page, size, status, lockerId, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KeyBorrowRecordDTO> getRecord(@PathVariable Long id) {
        return ResponseEntity.ok(keyBorrowService.getRecord(id));
    }

    /** 归还钥匙，归还人必填，归还时间默认当前时间 */
    @PostMapping("/{id}/return")
    public ResponseEntity<KeyBorrowRecordDTO> returnRecord(
            @PathVariable Long id,
            @RequestBody KeyBorrowReturnRequest request) {
        return ResponseEntity.ok(keyBorrowService.returnRecord(id, request));
    }

    /** 借用改期：仅借用中且预计归还已逾期可改，新预计归还必须更晚，改期原因必填；已归还、未到期的单不能改 */
    @PostMapping("/{id}/extend")
    public ResponseEntity<KeyBorrowRecordDTO> extendRecord(
            @PathVariable Long id,
            @RequestBody KeyBorrowExtendRequest request) {
        return ResponseEntity.ok(keyBorrowService.extendRecord(id, request));
    }

    /** 某台柜体的全部借用记录 */
    @GetMapping("/locker/{lockerId}")
    public ResponseEntity<List<KeyBorrowRecordDTO>> getLockerRecords(@PathVariable Long lockerId) {
        return ResponseEntity.ok(keyBorrowService.getLockerRecords(lockerId));
    }

    /** 按柜钥匙状态一览：全部柜体列出，借用标记与未还条数实时推导 */
    @GetMapping("/locker-overview")
    public ResponseEntity<List<LockerKeyBorrowOverviewDTO>> getLockerOverview() {
        return ResponseEntity.ok(keyBorrowService.getLockerOverview());
    }

    /** 登记可选柜体（含永久停用柜，用于补登历史借用） */
    @GetMapping("/locker-options")
    public ResponseEntity<List<KeyBorrowLockerOptionDTO>> getLockerOptions() {
        return ResponseEntity.ok(keyBorrowService.getLockerOptions());
    }

    @GetMapping("/statuses")
    public ResponseEntity<Map<String, String>> getStatuses() {
        return ResponseEntity.ok(keyBorrowService.getStatusMap());
    }
}
