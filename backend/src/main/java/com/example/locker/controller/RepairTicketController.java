package com.example.locker.controller;

import com.example.locker.dto.LockerRepairOverviewDTO;
import com.example.locker.dto.PageResponse;
import com.example.locker.dto.RepairTicketCompleteRequest;
import com.example.locker.dto.RepairTicketCreateRequest;
import com.example.locker.dto.RepairTicketDTO;
import com.example.locker.service.RepairTicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repairs")
@CrossOrigin(origins = "*")
public class RepairTicketController {

    @Autowired
    private RepairTicketService repairTicketService;

    /** 登记格口报修：故障格口、故障现象、报修人必填，未填齐不能建单；同一格口处理中不能重复报修 */
    @PostMapping
    public ResponseEntity<RepairTicketDTO> createTicket(@RequestBody RepairTicketCreateRequest request) {
        return ResponseEntity.ok(repairTicketService.createTicket(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<RepairTicketDTO>> getTickets(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            /** 报修状态：PROCESSING-处理中, FIXED-已修好 */
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId,
            /** 单号 / 格口 / 现象 / 报修人 / 柜体编号 关键字 */
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(repairTicketService.getTickets(page, size, status, lockerId, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepairTicketDTO> getTicket(@PathVariable Long id) {
        return ResponseEntity.ok(repairTicketService.getTicket(id));
    }

    /** 完工：处理人、处理结果必填，状态变为已修好；已修好的单不能重复完工 */
    @PostMapping("/{id}/complete")
    public ResponseEntity<RepairTicketDTO> completeTicket(
            @PathVariable Long id,
            @RequestBody RepairTicketCompleteRequest request) {
        return ResponseEntity.ok(repairTicketService.completeTicket(id, request));
    }

    /** 某台柜体的全部报修单（柜详情页报修条数与历史） */
    @GetMapping("/locker/{lockerId}")
    public ResponseEntity<List<RepairTicketDTO>> getLockerTickets(@PathVariable Long lockerId) {
        return ResponseEntity.ok(repairTicketService.getLockerTickets(lockerId));
    }

    /** 按柜报修状态一览：全部柜体列出，可用标记与处理中条数实时推导 */
    @GetMapping("/locker-overview")
    public ResponseEntity<List<LockerRepairOverviewDTO>> getLockerOverview() {
        return ResponseEntity.ok(repairTicketService.getLockerOverview());
    }

    @GetMapping("/statuses")
    public ResponseEntity<Map<String, String>> getStatuses() {
        return ResponseEntity.ok(repairTicketService.getStatusMap());
    }
}
