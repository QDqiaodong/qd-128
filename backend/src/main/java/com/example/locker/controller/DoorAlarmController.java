package com.example.locker.controller;

import com.example.locker.dto.DoorAlarmCloseRequest;
import com.example.locker.dto.DoorAlarmCreateRequest;
import com.example.locker.dto.DoorAlarmRecordDTO;
import com.example.locker.dto.LockerDoorAlarmOverviewDTO;
import com.example.locker.dto.PageResponse;
import com.example.locker.service.DoorAlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/door-alarms")
@CrossOrigin(origins = "*")
public class DoorAlarmController {

    @Autowired
    private DoorAlarmService doorAlarmService;

    /** 登记柜门未关告警：上报人必填；同一柜已有未处理告警时后端拦截 */
    @PostMapping
    public ResponseEntity<DoorAlarmRecordDTO> reportAlarm(@RequestBody DoorAlarmCreateRequest request) {
        return ResponseEntity.ok(doorAlarmService.reportAlarm(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<DoorAlarmRecordDTO>> getAlarms(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            /** 告警状态：OPEN-未处理, CLOSED-已关闭 */
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId,
            /** 告警编号 / 上报人 / 柜体编号 关键字 */
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(doorAlarmService.getAlarms(page, size, status, lockerId, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoorAlarmRecordDTO> getAlarm(@PathVariable Long id) {
        return ResponseEntity.ok(doorAlarmService.getAlarm(id));
    }

    /** 确认柜门已关严：同一条记录状态变为已关闭，柜体未关标记随之恢复；已关闭不能重复操作 */
    @PostMapping("/{id}/close")
    public ResponseEntity<DoorAlarmRecordDTO> closeAlarm(
            @PathVariable Long id,
            @RequestBody(required = false) DoorAlarmCloseRequest request) {
        return ResponseEntity.ok(doorAlarmService.closeAlarm(id, request));
    }

    /** 某台柜体的全部柜门未关告警（柜详情页告警记录） */
    @GetMapping("/locker/{lockerId}")
    public ResponseEntity<List<DoorAlarmRecordDTO>> getLockerAlarms(@PathVariable Long lockerId) {
        return ResponseEntity.ok(doorAlarmService.getLockerAlarms(lockerId));
    }

    /** 按柜门状态一览：全部柜体列出，未关标记与未处理条数实时推导 */
    @GetMapping("/locker-overview")
    public ResponseEntity<List<LockerDoorAlarmOverviewDTO>> getLockerOverview() {
        return ResponseEntity.ok(doorAlarmService.getLockerOverview());
    }

    @GetMapping("/statuses")
    public ResponseEntity<Map<String, String>> getStatuses() {
        return ResponseEntity.ok(doorAlarmService.getStatusMap());
    }
}
