package com.example.locker.service;

import com.example.locker.dto.ClearanceCompleteRequest;
import com.example.locker.dto.ClearanceOrderCreateRequest;
import com.example.locker.dto.ClearanceOrderDTO;
import com.example.locker.dto.LockerClearanceOverviewDTO;
import com.example.locker.entity.ClearanceOrder;
import com.example.locker.entity.Locker;
import com.example.locker.enums.ClearanceStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.ClearanceOrderRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClearanceServiceTest {

    @Mock
    private ClearanceOrderRepository clearanceOrderRepository;

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private ClearanceService clearanceService;

    private Locker locker;

    @BeforeEach
    void setUp() {
        locker = new Locker();
        locker.setId(1L);
        locker.setLockerNo("KDG-001");
        locker.setBuildingId(1L);
        locker.setUnitId(1L);
        locker.setStatus(LockerStatus.ACTIVE);
    }

    private ClearanceOrderCreateRequest validRequest() {
        ClearanceOrderCreateRequest request = new ClearanceOrderCreateRequest();
        request.setLockerId(1L);
        request.setOverdueCompartments("A03,A07");
        request.setPackageCount(2);
        request.setFoundTime(LocalDateTime.now().minusHours(3));
        request.setHandler("张师傅");
        return request;
    }

    @Test
    void createOrderPersistsSingleProcessingOrder() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(clearanceOrderRepository.existsByOrderNo(anyString())).thenReturn(false);
        when(clearanceOrderRepository.save(any(ClearanceOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClearanceOrderDTO dto = clearanceService.createOrder(validRequest());

        assertEquals(ClearanceStatus.PROCESSING, dto.getStatus());
        assertTrue(dto.getOverdue());
        assertEquals(2, dto.getPackageCount());
        assertEquals("A03,A07", dto.getOverdueCompartments());
        assertNotNull(dto.getOrderNo());
        verify(clearanceOrderRepository, times(1)).save(any(ClearanceOrder.class));
    }

    @Test
    void createOrderAllowsPermanentlyDisabledLockerForHistory() {
        // 永久停用柜仍可补登历史滞留
        locker.setStatus(LockerStatus.PERMANENTLY_DISABLED);
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(clearanceOrderRepository.existsByOrderNo(anyString())).thenReturn(false);
        when(clearanceOrderRepository.save(any(ClearanceOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClearanceOrderCreateRequest request = validRequest();
        request.setFoundTime(LocalDateTime.now().minusDays(30));

        ClearanceOrderDTO dto = clearanceService.createOrder(request);
        assertEquals(ClearanceStatus.PROCESSING, dto.getStatus());
        verify(clearanceOrderRepository).save(any(ClearanceOrder.class));
    }

    @Test
    void createOrderRejectsMissingRequiredFields() {
        ClearanceOrderCreateRequest noCompartment = validRequest();
        noCompartment.setOverdueCompartments(" ");
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createOrder(noCompartment));

        ClearanceOrderCreateRequest noCount = validRequest();
        noCount.setPackageCount(0);
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createOrder(noCount));

        ClearanceOrderCreateRequest noFoundTime = validRequest();
        noFoundTime.setFoundTime(null);
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createOrder(noFoundTime));

        ClearanceOrderCreateRequest futureFoundTime = validRequest();
        futureFoundTime.setFoundTime(LocalDateTime.now().plusHours(1));
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createOrder(futureFoundTime));

        ClearanceOrderCreateRequest noHandler = validRequest();
        noHandler.setHandler(null);
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createOrder(noHandler));

        // 任何校验失败都不能落库，避免留下半单
        verify(clearanceOrderRepository, never()).save(any(ClearanceOrder.class));
    }

    @Test
    void completeOrderRequiresHandleResult() {
        ClearanceCompleteRequest request = new ClearanceCompleteRequest();
        request.setHandleResult("  ");
        assertThrows(IllegalArgumentException.class,
                () -> clearanceService.completeOrder(1L, request));
        verify(clearanceOrderRepository, never()).save(any(ClearanceOrder.class));
    }

    @Test
    void completeOrderSetsResultAndTime() {
        ClearanceOrder order = new ClearanceOrder();
        order.setId(1L);
        order.setLockerId(1L);
        order.setStatus(ClearanceStatus.PROCESSING);
        when(clearanceOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(clearanceOrderRepository.save(any(ClearanceOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        ClearanceCompleteRequest request = new ClearanceCompleteRequest();
        request.setHandleResult("业主已取走，格口清空");
        ClearanceOrderDTO dto = clearanceService.completeOrder(1L, request);

        assertEquals(ClearanceStatus.COMPLETED, dto.getStatus());
        assertFalse(dto.getOverdue());
        assertEquals("业主已取走，格口清空", dto.getHandleResult());
        assertNotNull(dto.getCompleteTime());
    }

    @Test
    void completeOrderRejectsAlreadyCompleted() {
        ClearanceOrder order = new ClearanceOrder();
        order.setId(1L);
        order.setStatus(ClearanceStatus.COMPLETED);
        when(clearanceOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        ClearanceCompleteRequest request = new ClearanceCompleteRequest();
        request.setHandleResult("重复办结");
        assertThrows(IllegalArgumentException.class,
                () -> clearanceService.completeOrder(1L, request));
        verify(clearanceOrderRepository, never()).save(any(ClearanceOrder.class));
    }

    @Test
    void lockerOverviewShowsUnregisteredActiveLockers() {
        // 正常柜全量列出：无办理中清柜单的柜体 overdue=false，漏登可被发现
        Locker other = new Locker();
        other.setId(2L);
        other.setLockerNo("KDG-002");
        other.setBuildingId(1L);
        other.setUnitId(1L);
        other.setStatus(LockerStatus.ACTIVE);
        when(lockerRepository.findByStatus(LockerStatus.ACTIVE)).thenReturn(List.of(locker, other));

        ClearanceOrder open = new ClearanceOrder();
        open.setLockerId(1L);
        open.setPackageCount(3);
        open.setStatus(ClearanceStatus.PROCESSING);
        open.setFoundTime(LocalDateTime.now().minusHours(1));
        when(clearanceOrderRepository.findByLockerIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(open));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());

        List<LockerClearanceOverviewDTO> overview = clearanceService.getLockerOverview();

        assertEquals(2, overview.size());
        LockerClearanceOverviewDTO first = overview.get(0);
        assertEquals(1L, first.getLockerId());
        assertTrue(first.getOverdue());
        assertEquals(1, first.getOpenOrderCount());
        assertEquals(3, first.getOpenPackageCount());

        LockerClearanceOverviewDTO second = overview.get(1);
        assertEquals(2L, second.getLockerId());
        assertFalse(second.getOverdue());
        assertEquals(0, second.getOpenOrderCount());
    }
}
