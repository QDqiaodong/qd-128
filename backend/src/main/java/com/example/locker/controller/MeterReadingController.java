package com.example.locker.controller;

import com.example.locker.dto.*;
import com.example.locker.service.MeterReadingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meter-readings")
@CrossOrigin(origins = "*")
public class MeterReadingController {

    @Autowired
    private MeterReadingService meterReadingService;

    /** 登记电表抄表（账期由抄表时间推导；同一柜同一自然月只允许一张未作废单） */
    @PostMapping
    public ResponseEntity<MeterReadingRecordDTO> createRecord(@RequestBody MeterReadingCreateRequest request) {
        return ResponseEntity.ok(meterReadingService.createRecord(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<MeterReadingRecordDTO>> getRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            /** 账期（自然月，格式 yyyy-MM），不传返回全部账期 */
            @RequestParam(required = false) String periodMonth,
            /** 单据状态：ACTIVE-有效, VOIDED-已作废 */
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId,
            /** 单号 / 抄表人 / 柜体编号 关键字 */
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(meterReadingService.getRecords(page, size, periodMonth, status, lockerId, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeterReadingRecordDTO> getRecord(@PathVariable Long id) {
        return ResponseEntity.ok(meterReadingService.getRecord(id));
    }

    /** 作废抄表单，作废原因必填；作废后该柜该月可重新登记 */
    @PostMapping("/{id}/void")
    public ResponseEntity<MeterReadingRecordDTO> voidRecord(
            @PathVariable Long id,
            @RequestBody MeterReadingVoidRequest request) {
        return ResponseEntity.ok(meterReadingService.voidRecord(id, request));
    }

    /** 某台柜体的全部抄表单（含已作废） */
    @GetMapping("/locker/{lockerId}")
    public ResponseEntity<List<MeterReadingRecordDTO>> getLockerRecords(@PathVariable Long lockerId) {
        return ResponseEntity.ok(meterReadingService.getLockerRecords(lockerId));
    }

    /** 按柜本月抄表状态一览：全部柜体列出，已抄/未抄与本月读数实时推导 */
    @GetMapping("/locker-overview")
    public ResponseEntity<List<LockerMeterReadingOverviewDTO>> getLockerOverview(
            @RequestParam(required = false) String periodMonth) {
        return ResponseEntity.ok(meterReadingService.getLockerOverview(periodMonth));
    }

    /** 登记可选柜体（本月已抄的柜体标记置灰） */
    @GetMapping("/locker-options")
    public ResponseEntity<List<MeterReadingLockerOptionDTO>> getLockerOptions() {
        return ResponseEntity.ok(meterReadingService.getLockerOptions());
    }

    @GetMapping("/statuses")
    public ResponseEntity<Map<String, String>> getStatuses() {
        return ResponseEntity.ok(meterReadingService.getStatusMap());
    }
}
