package com.example.locker.service;

import com.example.locker.dto.ClearanceCompleteRequest;
import com.example.locker.dto.ClearanceOrderCreateRequest;
import com.example.locker.dto.ClearanceOrderDTO;
import com.example.locker.dto.ClearanceUrgeCloseRequest;
import com.example.locker.dto.ClearanceUrgeCreateRequest;
import com.example.locker.dto.ClearanceUrgeRecordDTO;
import com.example.locker.dto.LockerClearanceOverviewDTO;
import com.example.locker.entity.ClearanceOrder;
import com.example.locker.entity.ClearanceUrgeRecord;
import com.example.locker.entity.Locker;
import com.example.locker.enums.ClearanceStatus;
import com.example.locker.enums.ClearanceUrgeStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.ClearanceOrderRepository;
import com.example.locker.repository.ClearanceUrgeRecordRepository;
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

    @Mock
    private ClearanceUrgeRecordRepository urgeRepository;

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
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(clearanceOrderRepository.save(any(ClearanceOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(urgeRepository.findByOrderIdOrderByUrgeTimeDescIdDesc(1L))
                .thenReturn(Collections.emptyList());
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
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        ClearanceCompleteRequest request = new ClearanceCompleteRequest();
        request.setHandleResult("重复办结");
        assertThrows(IllegalArgumentException.class,
                () -> clearanceService.completeOrder(1L, request));
        verify(clearanceOrderRepository, never()).save(any(ClearanceOrder.class));
    }

    @Test
    void completeOrderAutoClosesOpenUrgeInSameTransaction() {
        // 办结时未关闭催领必须在同一事务内自动关闭，刷新后不能出现「已办结却催领中」
        ClearanceOrder order = new ClearanceOrder();
        order.setId(1L);
        order.setLockerId(1L);
        order.setStatus(ClearanceStatus.PROCESSING);
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(clearanceOrderRepository.save(any(ClearanceOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClearanceUrgeRecord openUrge = new ClearanceUrgeRecord();
        openUrge.setId(10L);
        openUrge.setOrderId(1L);
        openUrge.setStatus(ClearanceUrgeStatus.OPEN);
        when(urgeRepository.findByOrderIdOrderByUrgeTimeDescIdDesc(1L))
                .thenReturn(List.of(openUrge));
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        ClearanceCompleteRequest request = new ClearanceCompleteRequest();
        request.setHandleResult("业主已取走，格口清空");
        ClearanceOrderDTO dto = clearanceService.completeOrder(1L, request);

        assertEquals(ClearanceStatus.COMPLETED, dto.getStatus());
        assertEquals(ClearanceUrgeStatus.CLOSED, openUrge.getStatus());
        assertTrue(openUrge.getAutoClosed());
        assertNotNull(openUrge.getCloseTime());
        verify(urgeRepository).save(openUrge);
    }

    @Test
    void createUrgePersistsOpenRecordWithTimeAndOperator() {
        ClearanceOrder order = processingOrder();
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(urgeRepository.findFirstByOrderIdAndStatusOrderByIdAsc(1L, ClearanceUrgeStatus.OPEN))
                .thenReturn(Optional.empty());
        when(urgeRepository.save(any(ClearanceUrgeRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClearanceUrgeCreateRequest request = new ClearanceUrgeCreateRequest();
        request.setUrgeTime(LocalDateTime.now().minusMinutes(10));
        request.setOperator("赵管家");
        ClearanceUrgeRecordDTO dto = clearanceService.createUrge(1L, request);

        assertEquals(ClearanceUrgeStatus.OPEN.name(), dto.getStatus());
        assertEquals("赵管家", dto.getOperator());
        assertNotNull(dto.getUrgeTime());
        verify(urgeRepository, times(1)).save(any(ClearanceUrgeRecord.class));
    }

    @Test
    void createUrgeDefaultsUrgeTimeToNow() {
        ClearanceOrder order = processingOrder();
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(urgeRepository.findFirstByOrderIdAndStatusOrderByIdAsc(1L, ClearanceUrgeStatus.OPEN))
                .thenReturn(Optional.empty());
        when(urgeRepository.save(any(ClearanceUrgeRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClearanceUrgeCreateRequest request = new ClearanceUrgeCreateRequest();
        request.setOperator("赵管家");
        ClearanceUrgeRecordDTO dto = clearanceService.createUrge(1L, request);

        assertNotNull(dto.getUrgeTime());
    }

    @Test
    void createUrgeRejectedWhenAnotherOpenUrgeExists() {
        // 同一张办理中的单不能同时挂两笔未关闭催领
        ClearanceOrder order = processingOrder();
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        ClearanceUrgeRecord openUrge = new ClearanceUrgeRecord();
        openUrge.setId(10L);
        openUrge.setOrderId(1L);
        openUrge.setStatus(ClearanceUrgeStatus.OPEN);
        when(urgeRepository.findFirstByOrderIdAndStatusOrderByIdAsc(1L, ClearanceUrgeStatus.OPEN))
                .thenReturn(Optional.of(openUrge));

        ClearanceUrgeCreateRequest request = new ClearanceUrgeCreateRequest();
        request.setOperator("赵管家");
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createUrge(1L, request));
        // 拦截后不能写出半条
        verify(urgeRepository, never()).save(any(ClearanceUrgeRecord.class));
    }

    @Test
    void createUrgeRejectedForCompletedOrder() {
        ClearanceOrder order = processingOrder();
        order.setStatus(ClearanceStatus.COMPLETED);
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        ClearanceUrgeCreateRequest request = new ClearanceUrgeCreateRequest();
        request.setOperator("赵管家");
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createUrge(1L, request));
        verify(urgeRepository, never()).save(any(ClearanceUrgeRecord.class));
    }

    @Test
    void createUrgeRejectsMissingOperatorAndFutureTime() {
        ClearanceOrder order = processingOrder();
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        ClearanceUrgeCreateRequest noOperator = new ClearanceUrgeCreateRequest();
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createUrge(1L, noOperator));

        ClearanceUrgeCreateRequest future = new ClearanceUrgeCreateRequest();
        future.setOperator("赵管家");
        future.setUrgeTime(LocalDateTime.now().plusHours(1));
        assertThrows(IllegalArgumentException.class, () -> clearanceService.createUrge(1L, future));

        verify(urgeRepository, never()).save(any(ClearanceUrgeRecord.class));
    }

    @Test
    void closeUrgeClosesRecordAndAllowsNextUrge() {
        // 关闭后再记一笔：同一时刻只有一笔未关闭，但次数累计
        ClearanceOrder order = processingOrder();
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        ClearanceUrgeRecord openUrge = new ClearanceUrgeRecord();
        openUrge.setId(10L);
        openUrge.setOrderId(1L);
        openUrge.setStatus(ClearanceUrgeStatus.OPEN);
        when(urgeRepository.findById(10L)).thenReturn(Optional.of(openUrge));
        when(urgeRepository.save(any(ClearanceUrgeRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClearanceUrgeCloseRequest closeRequest = new ClearanceUrgeCloseRequest();
        closeRequest.setCloseNote("业主承诺明天取件");
        ClearanceUrgeRecordDTO closed = clearanceService.closeUrge(1L, 10L, closeRequest);

        assertEquals(ClearanceUrgeStatus.CLOSED.name(), closed.getStatus());
        assertEquals("业主承诺明天取件", closed.getCloseNote());
        assertNotNull(closed.getCloseTime());
        assertFalse(closed.getAutoClosed());

        // 关闭后无未关闭催领，允许再登记
        when(urgeRepository.findFirstByOrderIdAndStatusOrderByIdAsc(1L, ClearanceUrgeStatus.OPEN))
                .thenReturn(Optional.empty());
        ClearanceUrgeCreateRequest next = new ClearanceUrgeCreateRequest();
        next.setOperator("钱管家");
        clearanceService.createUrge(1L, next);
        verify(urgeRepository, times(2)).save(any(ClearanceUrgeRecord.class));
    }

    @Test
    void closeUrgeRejectsAlreadyClosed() {
        ClearanceOrder order = processingOrder();
        when(clearanceOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        ClearanceUrgeRecord closed = new ClearanceUrgeRecord();
        closed.setId(10L);
        closed.setOrderId(1L);
        closed.setStatus(ClearanceUrgeStatus.CLOSED);
        when(urgeRepository.findById(10L)).thenReturn(Optional.of(closed));

        assertThrows(IllegalArgumentException.class,
                () -> clearanceService.closeUrge(1L, 10L, new ClearanceUrgeCloseRequest()));
        verify(urgeRepository, never()).save(any(ClearanceUrgeRecord.class));
    }

    private ClearanceOrder processingOrder() {
        ClearanceOrder order = new ClearanceOrder();
        order.setId(1L);
        order.setLockerId(1L);
        order.setOrderNo("QG20260901001");
        order.setOverdueCompartments("A03");
        order.setPackageCount(1);
        order.setFoundTime(LocalDateTime.now().minusDays(2));
        order.setHandler("张师傅");
        order.setStatus(ClearanceStatus.PROCESSING);
        return order;
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
