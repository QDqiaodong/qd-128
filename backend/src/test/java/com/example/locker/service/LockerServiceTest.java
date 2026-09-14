package com.example.locker.service;

import com.example.locker.dto.FilterRequest;
import com.example.locker.dto.LockerDTO;
import com.example.locker.dto.PageResponse;
import com.example.locker.entity.CollectionSuspensionRecord;
import com.example.locker.entity.Locker;
import com.example.locker.enums.ClearanceStatus;
import com.example.locker.enums.CollectionSuspensionStatus;
import com.example.locker.enums.DoorAlarmStatus;
import com.example.locker.enums.KeyBorrowStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.enums.MeterReadingStatus;
import com.example.locker.enums.RepairStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.ClearanceOrderRepository;
import com.example.locker.repository.CollectionSuspensionRecordRepository;
import com.example.locker.repository.DoorAlarmRecordRepository;
import com.example.locker.repository.KeyBorrowRecordRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.MeterReadingRecordRepository;
import com.example.locker.repository.RepairTicketRepository;
import com.example.locker.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockerServiceTest {

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private ClearanceOrderRepository clearanceOrderRepository;

    @Mock
    private KeyBorrowRecordRepository keyBorrowRecordRepository;

    @Mock
    private MeterReadingRecordRepository meterReadingRecordRepository;

    @Mock
    private RepairTicketRepository repairTicketRepository;

    @Mock
    private DoorAlarmRecordRepository doorAlarmRecordRepository;

    @Mock
    private CollectionSuspensionRecordRepository collectionSuspensionRecordRepository;

    @Mock
    private SpecTemplateService specTemplateService;

    @InjectMocks
    private LockerService lockerService;

    private Locker suspendedLocker;
    private Locker normalLocker;

    @BeforeEach
    void setUp() {
        suspendedLocker = buildLocker(1L, "KDG-001");
        normalLocker = buildLocker(2L, "KDG-002");
    }

    private Locker buildLocker(Long id, String lockerNo) {
        Locker locker = new Locker();
        locker.setId(id);
        locker.setLockerNo(lockerNo);
        locker.setCompartmentCount(24);
        locker.setSpecType("STANDARD");
        locker.setBuildingId(1L);
        locker.setUnitId(1L);
        locker.setStatus(LockerStatus.ACTIVE);
        return locker;
    }

    /** 多条件筛选公共打桩：规格名称、楼栋/单元及其余派生标记台账均为空 */
    private void stubFilterBasics(List<Locker> lockers) {
        when(lockerRepository.findAll(ArgumentMatchers.<Specification<Locker>>any(), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(lockers));
        when(specTemplateService.getSpecTypeName(anyString())).thenReturn("标准型");
        when(buildingRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(unitRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(clearanceOrderRepository.findByLockerIdInAndStatus(anyList(), eq(ClearanceStatus.PROCESSING)))
                .thenReturn(Collections.emptyList());
        when(keyBorrowRecordRepository.findByLockerIdInAndStatus(anyList(), eq(KeyBorrowStatus.ON_LOAN)))
                .thenReturn(Collections.emptyList());
        when(meterReadingRecordRepository.findByLockerIdInAndPeriodMonthAndStatus(
                anyList(), anyString(), eq(MeterReadingStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());
        when(repairTicketRepository.findByLockerIdInAndStatus(anyList(), eq(RepairStatus.PROCESSING)))
                .thenReturn(Collections.emptyList());
        when(doorAlarmRecordRepository.findByLockerIdInAndStatus(anyList(), eq(DoorAlarmStatus.OPEN)))
                .thenReturn(Collections.emptyList());
    }

    private CollectionSuspensionRecord suspendedRecord(Long lockerId) {
        CollectionSuspensionRecord record = new CollectionSuspensionRecord();
        record.setLockerId(lockerId);
        record.setSuspendStartTime(LocalDateTime.now().minusHours(2));
        record.setExpectedResumeTime(LocalDateTime.now().plusHours(5));
        record.setStatus(CollectionSuspensionStatus.SUSPENDED);
        return record;
    }

    private FilterRequest defaultFilter() {
        FilterRequest request = new FilterRequest();
        request.setPage(1);
        request.setSize(20);
        return request;
    }

    private LockerDTO findById(PageResponse<LockerDTO> page, Long id) {
        return page.getData().stream()
                .filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void filterLockersMarksCollectionSuspendedConsistentWithDetail() {
        // 正在停收的柜：筛选结果与柜详情都应标出停收中，条数与停收台账一致
        stubFilterBasics(List.of(suspendedLocker, normalLocker));
        when(collectionSuspensionRecordRepository.findByLockerIdInAndStatus(
                anyList(), eq(CollectionSuspensionStatus.SUSPENDED)))
                .thenReturn(List.of(suspendedRecord(1L)));
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(suspendedLocker));

        PageResponse<LockerDTO> page = lockerService.filterLockers(defaultFilter());

        LockerDTO suspended = findById(page, 1L);
        assertTrue(suspended.getCollectionSuspended(), "筛选结果应对正在停收的柜标出停收中");
        assertEquals(1, suspended.getOpenCollectionSuspensionCount());

        LockerDTO normal = findById(page, 2L);
        assertFalse(normal.getCollectionSuspended(), "无停收中记录的柜不应误标停收中");
        assertEquals(0, normal.getOpenCollectionSuspensionCount());

        LockerDTO detail = lockerService.getLockerById(1L);
        assertTrue(detail.getCollectionSuspended(), "柜详情同样应为停收中");
        assertEquals(detail.getCollectionSuspended(), suspended.getCollectionSuspended(),
                "筛选结果与柜详情的停收中标记应一致");
        assertEquals(detail.getOpenCollectionSuspensionCount(), suspended.getOpenCollectionSuspensionCount(),
                "筛选结果与柜详情的停收中条数应一致");
    }

    @Test
    void filterLockersRederivesSuspensionMarkOnEachSearch() {
        // 停收中标记每次筛选都实时推导：台账仍是停收中时刷新再筛标记还在；
        // 确认恢复后台账清空，再筛标记随之消失
        stubFilterBasics(List.of(suspendedLocker));
        when(collectionSuspensionRecordRepository.findByLockerIdInAndStatus(
                anyList(), eq(CollectionSuspensionStatus.SUSPENDED)))
                .thenReturn(List.of(suspendedRecord(1L)));

        PageResponse<LockerDTO> first = lockerService.filterLockers(defaultFilter());
        assertTrue(first.getData().get(0).getCollectionSuspended());

        // 刷新后按同样条件再筛一次：台账未变，标记仍在
        PageResponse<LockerDTO> second = lockerService.filterLockers(defaultFilter());
        assertTrue(second.getData().get(0).getCollectionSuspended(), "刷新后再筛，停收中标记应仍在");

        // 确认恢复后台账不再有停收中记录，再筛标记消失
        when(collectionSuspensionRecordRepository.findByLockerIdInAndStatus(
                anyList(), eq(CollectionSuspensionStatus.SUSPENDED)))
                .thenReturn(Collections.emptyList());
        PageResponse<LockerDTO> afterResume = lockerService.filterLockers(defaultFilter());
        assertFalse(afterResume.getData().get(0).getCollectionSuspended(), "确认恢复后再筛，停收中标记应消失");
        assertEquals(0, afterResume.getData().get(0).getOpenCollectionSuspensionCount());
    }
}
