package com.example.locker.service;

import com.example.locker.dto.LockerRepairOverviewDTO;
import com.example.locker.dto.RepairTicketCompleteRequest;
import com.example.locker.dto.RepairTicketCreateRequest;
import com.example.locker.dto.RepairTicketDTO;
import com.example.locker.entity.Locker;
import com.example.locker.entity.RepairTicket;
import com.example.locker.enums.LockerStatus;
import com.example.locker.enums.RepairStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.RepairTicketRepository;
import com.example.locker.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairTicketServiceTest {

    @Mock
    private RepairTicketRepository repairTicketRepository;

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private RepairTicketService repairTicketService;

    private Locker locker;

    @BeforeEach
    void setUp() {
        locker = new Locker();
        locker.setId(1L);
        locker.setLockerNo("KDG-001");
        locker.setCompartmentCount(24);
        locker.setBuildingId(1L);
        locker.setUnitId(1L);
        locker.setStatus(LockerStatus.ACTIVE);
    }

    private RepairTicketCreateRequest validRequest() {
        RepairTicketCreateRequest request = new RepairTicketCreateRequest();
        request.setLockerId(1L);
        request.setCompartmentNo("5");
        request.setSymptom("5号格口门磁失灵，关门后指示灯不亮");
        request.setReporter("业主刘先生");
        return request;
    }

    @Test
    void createTicketPersistsProcessingTicket() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(repairTicketRepository.existsByLockerIdAndCompartmentNoAndStatus(1L, "5", RepairStatus.PROCESSING))
                .thenReturn(false);
        when(repairTicketRepository.existsByTicketNo(anyString())).thenReturn(false);
        when(repairTicketRepository.save(any(RepairTicket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RepairTicketDTO dto = repairTicketService.createTicket(validRequest());

        assertEquals(RepairStatus.PROCESSING, dto.getStatus());
        assertTrue(dto.getProcessing(), "建单后应为处理中");
        assertEquals("5", dto.getCompartmentNo());
        assertEquals("5号格口门磁失灵，关门后指示灯不亮", dto.getSymptom());
        assertEquals("业主刘先生", dto.getReporter());
        assertNotNull(dto.getTicketNo());
        verify(repairTicketRepository, times(1)).save(any(RepairTicket.class));
    }

    @Test
    void createTicketRejectsMissingRequiredFields() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        RepairTicketCreateRequest noCompartment = validRequest();
        noCompartment.setCompartmentNo(" ");
        assertThrows(IllegalArgumentException.class, () -> repairTicketService.createTicket(noCompartment));

        RepairTicketCreateRequest noSymptom = validRequest();
        noSymptom.setSymptom(null);
        assertThrows(IllegalArgumentException.class, () -> repairTicketService.createTicket(noSymptom));

        RepairTicketCreateRequest noReporter = validRequest();
        noReporter.setReporter("  ");
        assertThrows(IllegalArgumentException.class, () -> repairTicketService.createTicket(noReporter));

        assertThrows(IllegalArgumentException.class, () -> repairTicketService.createTicket(null));

        // 任何校验失败都不能落库，避免写出半条报修单
        verify(repairTicketRepository, never()).save(any(RepairTicket.class));
    }

    @Test
    void createTicketRejectsCompartmentOutOfRange() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        RepairTicketCreateRequest request = validRequest();
        request.setCompartmentNo("25");
        assertThrows(IllegalArgumentException.class, () -> repairTicketService.createTicket(request));

        RepairTicketCreateRequest zero = validRequest();
        zero.setCompartmentNo("0");
        assertThrows(IllegalArgumentException.class, () -> repairTicketService.createTicket(zero));

        verify(repairTicketRepository, never()).save(any(RepairTicket.class));
    }

    @Test
    void createTicketRejectsDuplicateOpenTicketOnSameCompartment() {
        // 同一柜同一格口已有处理中报修单时不能重复建单
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(repairTicketRepository.existsByLockerIdAndCompartmentNoAndStatus(1L, "5", RepairStatus.PROCESSING))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> repairTicketService.createTicket(validRequest()));
        verify(repairTicketRepository, never()).save(any(RepairTicket.class));
    }

    @Test
    void completeTicketRequiresHandlerAndResult() {
        RepairTicketCompleteRequest noHandler = new RepairTicketCompleteRequest();
        noHandler.setRepairResult("已更换锁芯");
        assertThrows(IllegalArgumentException.class,
                () -> repairTicketService.completeTicket(1L, noHandler));

        RepairTicketCompleteRequest noResult = new RepairTicketCompleteRequest();
        noResult.setHandler("张师傅");
        assertThrows(IllegalArgumentException.class,
                () -> repairTicketService.completeTicket(1L, noResult));

        assertThrows(IllegalArgumentException.class,
                () -> repairTicketService.completeTicket(1L, null));
        verify(repairTicketRepository, never()).save(any(RepairTicket.class));
    }

    @Test
    void completeTicketSetsFixedStatusAndHandler() {
        RepairTicket ticket = new RepairTicket();
        ticket.setId(1L);
        ticket.setLockerId(1L);
        ticket.setStatus(RepairStatus.PROCESSING);
        when(repairTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(repairTicketRepository.save(any(RepairTicket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        RepairTicketCompleteRequest request = new RepairTicketCompleteRequest();
        request.setHandler("张师傅");
        request.setRepairResult("更换锁芯并调试，开关恢复正常");
        RepairTicketDTO dto = repairTicketService.completeTicket(1L, request);

        assertEquals(RepairStatus.FIXED, dto.getStatus());
        assertFalse(dto.getProcessing(), "完工后不再是处理中");
        assertEquals("张师傅", dto.getHandler());
        assertEquals("更换锁芯并调试，开关恢复正常", dto.getRepairResult());
        assertNotNull(dto.getFixedTime());
    }

    @Test
    void completeTicketRejectsAlreadyFixed() {
        RepairTicket ticket = new RepairTicket();
        ticket.setId(1L);
        ticket.setStatus(RepairStatus.FIXED);
        when(repairTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        RepairTicketCompleteRequest request = new RepairTicketCompleteRequest();
        request.setHandler("张师傅");
        request.setRepairResult("重复完工");
        assertThrows(IllegalArgumentException.class,
                () -> repairTicketService.completeTicket(1L, request));
        verify(repairTicketRepository, never()).save(any(RepairTicket.class));
    }

    @Test
    void lockerOverviewMarksRepairingLockers() {
        // 全部柜体列出：维修中的柜体置顶并标记，处理中条数实时推导
        Locker other = new Locker();
        other.setId(2L);
        other.setLockerNo("KDG-002");
        other.setBuildingId(1L);
        other.setUnitId(1L);
        other.setStatus(LockerStatus.ACTIVE);
        when(lockerRepository.findAll(any(Sort.class))).thenReturn(List.of(locker, other));

        RepairTicket open = new RepairTicket();
        open.setLockerId(2L);
        open.setStatus(RepairStatus.PROCESSING);
        open.setCreateTime(LocalDateTime.now().minusHours(1));
        RepairTicket fixed = new RepairTicket();
        fixed.setLockerId(2L);
        fixed.setStatus(RepairStatus.FIXED);
        fixed.setCreateTime(LocalDateTime.now().minusDays(3));
        when(repairTicketRepository.findByLockerIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(open, fixed));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());

        List<LockerRepairOverviewDTO> overview = repairTicketService.getLockerOverview();

        assertEquals(2, overview.size());
        LockerRepairOverviewDTO first = overview.get(0);
        assertEquals(2L, first.getLockerId());
        assertTrue(first.getRepairing());
        assertEquals(1, first.getOpenTicketCount());
        assertEquals(2, first.getTotalTicketCount());

        LockerRepairOverviewDTO second = overview.get(1);
        assertEquals(1L, second.getLockerId());
        assertFalse(second.getRepairing());
        assertEquals(0, second.getOpenTicketCount());
    }
}
