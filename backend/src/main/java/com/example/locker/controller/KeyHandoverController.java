package com.example.locker.controller;

import com.example.locker.dto.KeyHandoverCreateRequest;
import com.example.locker.dto.KeyHandoverDTO;
import com.example.locker.dto.KeyHandoverPendingItemDTO;
import com.example.locker.dto.PageResponse;
import com.example.locker.service.KeyHandoverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 钥匙交接班：在借用台账里对当前全部未还柜点名后提交交接。
 */
@RestController
@RequestMapping("/api/key-handovers")
@CrossOrigin(origins = "*")
public class KeyHandoverController {

    @Autowired
    private KeyHandoverService keyHandoverService;

    /** 交接窗口待点名清单：当前全部借用中记录，实时由台账推导 */
    @GetMapping("/pending")
    public ResponseEntity<List<KeyHandoverPendingItemDTO>> getPendingItems() {
        return ResponseEntity.ok(keyHandoverService.getPendingItems());
    }

    /** 提交交接：必须勾齐全部未还柜，交班人/接班人/交接说明必填；交接不改借用状态 */
    @PostMapping
    public ResponseEntity<KeyHandoverDTO> submitHandover(@RequestBody KeyHandoverCreateRequest request) {
        return ResponseEntity.ok(keyHandoverService.submitHandover(request));
    }

    /** 交接痕迹分页（最近在前） */
    @GetMapping
    public ResponseEntity<PageResponse<KeyHandoverDTO>> getHandovers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(keyHandoverService.getHandovers(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KeyHandoverDTO> getHandover(@PathVariable Long id) {
        return ResponseEntity.ok(keyHandoverService.getHandover(id));
    }

    /** 某条借用记录被点名过的交接痕迹 */
    @GetMapping("/by-record/{recordId}")
    public ResponseEntity<List<KeyHandoverDTO>> getRecordHandovers(@PathVariable Long recordId) {
        return ResponseEntity.ok(keyHandoverService.getRecordHandovers(recordId));
    }

    /** 某台柜体相关的全部交接痕迹（柜详情页） */
    @GetMapping("/by-locker/{lockerId}")
    public ResponseEntity<List<KeyHandoverDTO>> getLockerHandovers(@PathVariable Long lockerId) {
        return ResponseEntity.ok(keyHandoverService.getLockerHandovers(lockerId));
    }
}
