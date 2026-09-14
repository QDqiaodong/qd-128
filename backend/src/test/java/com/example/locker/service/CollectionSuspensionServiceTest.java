package com.example.locker.service;

import com.example.locker.LockerManagementApplication;
import com.example.locker.dto.CollectionSuspensionCreateRequest;
import com.example.locker.dto.CollectionSuspensionRecordDTO;
import com.example.locker.dto.CollectionSuspensionResumeRequest;
import com.example.locker.dto.LockerCollectionSuspensionOverviewDTO;
import com.example.locker.entity.CollectionSuspensionRecord;
import com.example.locker.entity.Locker;
import com.example.locker.enums.CollectionSuspensionStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.CollectionSuspensionRecordRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionSuspensionServiceTest {

    @Mock
    private CollectionSuspensionRecordRepository collectionSuspensionRecordRepository;

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private CollectionSuspensionService collectionSuspensionService;

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

    private CollectionSuspensionCreateRequest validRequest() {
        CollectionSuspensionCreateRequest request = new CollectionSuspensionCreateRequest();
        request.setLockerId(1L);
        request.setSuspendStartTime(LocalDateTime.now().minusHours(1));
        request.setExpectedResumeTime(LocalDateTime.now().plusHours(8));
        request.setDutyOfficer("夜班-陈师傅");
        return request;
    }

    @Test
    void registerPersistsSuspendedRecord() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(collectionSuspensionRecordRepository.existsByLockerIdAndStatus(
                1L, CollectionSuspensionStatus.SUSPENDED)).thenReturn(false);
        when(collectionSuspensionRecordRepository.existsByRecordNo(anyString())).thenReturn(false);
        when(collectionSuspensionRecordRepository.save(any(CollectionSuspensionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CollectionSuspensionRecordDTO dto = collectionSuspensionService.register(validRequest());

        assertEquals(CollectionSuspensionStatus.SUSPENDED, dto.getStatus());
        assertTrue(dto.getSuspended(), "登记后应为停收中");
        assertEquals("夜班-陈师傅", dto.getDutyOfficer());
        assertNotNull(dto.getRecordNo());
        verify(collectionSuspensionRecordRepository, times(1)).save(any(CollectionSuspensionRecord.class));
    }

    @Test
    void registerDefaultsStartTimeToNow() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(collectionSuspensionRecordRepository.existsByLockerIdAndStatus(
                1L, CollectionSuspensionStatus.SUSPENDED)).thenReturn(false);
        when(collectionSuspensionRecordRepository.existsByRecordNo(anyString())).thenReturn(false);
        when(collectionSuspensionRecordRepository.save(any(CollectionSuspensionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CollectionSuspensionCreateRequest request = validRequest();
        request.setSuspendStartTime(null);
        CollectionSuspensionRecordDTO dto = collectionSuspensionService.register(request);

        assertNotNull(dto.getSuspendStartTime());
    }

    @Test
    void registerRejectsMissingRequiredFields() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        // 缺值班人
        CollectionSuspensionCreateRequest noDuty = validRequest();
        noDuty.setDutyOfficer("  ");
        assertThrows(IllegalArgumentException.class, () -> collectionSuspensionService.register(noDuty));

        // 缺预计恢复时间
        CollectionSuspensionCreateRequest noExpected = validRequest();
        noExpected.setExpectedResumeTime(null);
        assertThrows(IllegalArgumentException.class, () -> collectionSuspensionService.register(noExpected));

        // 缺柜体
        assertThrows(IllegalArgumentException.class, () -> collectionSuspensionService.register(null));

        // 任何校验失败都不能落库，避免写出半条台账
        verify(collectionSuspensionRecordRepository, never()).save(any(CollectionSuspensionRecord.class));
    }

    @Test
    void registerRejectsFutureStartTime() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        CollectionSuspensionCreateRequest request = validRequest();
        request.setSuspendStartTime(LocalDateTime.now().plusMinutes(5));
        request.setExpectedResumeTime(LocalDateTime.now().plusHours(9));
        assertThrows(IllegalArgumentException.class, () -> collectionSuspensionService.register(request));
        verify(collectionSuspensionRecordRepository, never()).save(any(CollectionSuspensionRecord.class));
    }

    @Test
    void registerAcceptsBrowserNowWhenContainerDefaultsToUtc() {
        // 回归：容器（eclipse-temurin）JVM 默认 UTC，页面按北京时间生成「当前时刻」提交，
        // 未统一时区时会被「开始停收时间不能晚于当前时间」误判拦截，台账留不下记录。
        // 应用启动统一为北京时间后，页面当前时刻 + 次日清晨恢复时间 + 值班人应能登记成功。
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LockerManagementApplication.initBusinessTimeZone();

            LocalDateTime pageNow = LocalDateTime.now(ZoneId.of(LockerManagementApplication.BUSINESS_ZONE_ID));
            LocalDateTime expectedResume = pageNow.toLocalDate().plusDays(1).atTime(7, 0);

            when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
            when(collectionSuspensionRecordRepository.existsByLockerIdAndStatus(
                    1L, CollectionSuspensionStatus.SUSPENDED)).thenReturn(false);
            when(collectionSuspensionRecordRepository.existsByRecordNo(anyString())).thenReturn(false);
            when(collectionSuspensionRecordRepository.save(any(CollectionSuspensionRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CollectionSuspensionCreateRequest request = new CollectionSuspensionCreateRequest();
            request.setLockerId(1L);
            request.setSuspendStartTime(pageNow);
            request.setExpectedResumeTime(expectedResume);
            request.setDutyOfficer("夜班-陈师傅");

            CollectionSuspensionRecordDTO dto = collectionSuspensionService.register(request);

            assertEquals(CollectionSuspensionStatus.SUSPENDED, dto.getStatus());
            assertTrue(dto.getSuspended(), "用页面默认时间登记后应为停收中");
            verify(collectionSuspensionRecordRepository, times(1)).save(any(CollectionSuspensionRecord.class));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void registerRejectsExpectedResumeNotAfterStart() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        CollectionSuspensionCreateRequest request = validRequest();
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        request.setSuspendStartTime(start);
        request.setExpectedResumeTime(start);
        assertThrows(IllegalArgumentException.class, () -> collectionSuspensionService.register(request));
        verify(collectionSuspensionRecordRepository, never()).save(any(CollectionSuspensionRecord.class));
    }

    @Test
    void registerRejectsLockerAlreadySuspended() {
        // 同一柜已有停收中记录时不能重复登记，先确认恢复后再登记
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(collectionSuspensionRecordRepository.existsByLockerIdAndStatus(
                1L, CollectionSuspensionStatus.SUSPENDED)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> collectionSuspensionService.register(validRequest()));
        verify(collectionSuspensionRecordRepository, never()).save(any(CollectionSuspensionRecord.class));
    }

    @Test
    void resumeChangesSameRecordToResumed() {
        // 确认已恢复：同一条台账记录状态流转为已恢复，不新增也不删除，
        // 台账按停收中/已恢复筛选找到的都是这同一条
        CollectionSuspensionRecord record = new CollectionSuspensionRecord();
        record.setId(7L);
        record.setRecordNo("TS20260913001");
        record.setLockerId(1L);
        record.setSuspendStartTime(LocalDateTime.now().minusHours(9));
        record.setExpectedResumeTime(LocalDateTime.now().minusMinutes(5));
        record.setDutyOfficer("夜班-陈师傅");
        record.setStatus(CollectionSuspensionStatus.SUSPENDED);
        when(collectionSuspensionRecordRepository.findById(7L)).thenReturn(Optional.of(record));
        when(collectionSuspensionRecordRepository.save(any(CollectionSuspensionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CollectionSuspensionRecordDTO dto =
                collectionSuspensionService.resume(7L, new CollectionSuspensionResumeRequest());

        assertEquals(7L, dto.getId(), "确认恢复后仍是同一条台账记录");
        assertEquals("TS20260913001", dto.getRecordNo());
        assertEquals(CollectionSuspensionStatus.RESUMED, dto.getStatus());
        assertFalse(dto.getSuspended(), "确认恢复后不再是停收中");
        assertFalse(dto.getOverdue(), "已恢复记录不再算逾时未恢复");
        assertEquals("系统管理员", dto.getResumeOperator(), "恢复人留空默认系统管理员");
        assertNotNull(dto.getResumeTime());
    }

    @Test
    void resumeRejectsAlreadyResumed() {
        CollectionSuspensionRecord record = new CollectionSuspensionRecord();
        record.setId(7L);
        record.setLockerId(1L);
        record.setStatus(CollectionSuspensionStatus.RESUMED);
        when(collectionSuspensionRecordRepository.findById(7L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class,
                () -> collectionSuspensionService.resume(7L, new CollectionSuspensionResumeRequest()));
        verify(collectionSuspensionRecordRepository, never()).save(any(CollectionSuspensionRecord.class));
    }

    @Test
    void overdueDerivedFromExpectedResumeTime() {
        // 已过预计恢复时间仍未确认恢复：停收中且预计恢复时间已过 → 逾时
        CollectionSuspensionRecord overdueRecord = new CollectionSuspensionRecord();
        overdueRecord.setId(1L);
        overdueRecord.setLockerId(1L);
        overdueRecord.setSuspendStartTime(LocalDateTime.now().minusHours(12));
        overdueRecord.setExpectedResumeTime(LocalDateTime.now().minusMinutes(30));
        overdueRecord.setStatus(CollectionSuspensionStatus.SUSPENDED);
        when(collectionSuspensionRecordRepository.findById(1L)).thenReturn(Optional.of(overdueRecord));
        assertTrue(collectionSuspensionService.getRecord(1L).getOverdue(), "已过预计恢复时间仍未恢复必须标记逾时");

        // 尚未到预计恢复时间：不逾时
        CollectionSuspensionRecord withinRecord = new CollectionSuspensionRecord();
        withinRecord.setId(2L);
        withinRecord.setLockerId(1L);
        withinRecord.setSuspendStartTime(LocalDateTime.now().minusHours(1));
        withinRecord.setExpectedResumeTime(LocalDateTime.now().plusHours(6));
        withinRecord.setStatus(CollectionSuspensionStatus.SUSPENDED);
        when(collectionSuspensionRecordRepository.findById(2L)).thenReturn(Optional.of(withinRecord));
        assertFalse(collectionSuspensionService.getRecord(2L).getOverdue(), "未到预计恢复时间不应标记逾时");
    }

    @Test
    void lockerOverviewMarksSuspendedLockers() {
        // 全部柜体列出：停收中的柜体置顶并标记，停收中条数实时推导
        Locker other = new Locker();
        other.setId(2L);
        other.setLockerNo("KDG-002");
        other.setBuildingId(1L);
        other.setUnitId(1L);
        other.setStatus(LockerStatus.ACTIVE);
        when(lockerRepository.findAll(any(Sort.class))).thenReturn(List.of(locker, other));

        CollectionSuspensionRecord open = new CollectionSuspensionRecord();
        open.setLockerId(2L);
        open.setSuspendStartTime(LocalDateTime.now().minusHours(10));
        open.setExpectedResumeTime(LocalDateTime.now().minusMinutes(10));
        open.setStatus(CollectionSuspensionStatus.SUSPENDED);
        CollectionSuspensionRecord resumed = new CollectionSuspensionRecord();
        resumed.setLockerId(2L);
        resumed.setSuspendStartTime(LocalDateTime.now().minusDays(3));
        resumed.setExpectedResumeTime(LocalDateTime.now().minusDays(3).plusHours(9));
        resumed.setStatus(CollectionSuspensionStatus.RESUMED);
        when(collectionSuspensionRecordRepository.findByLockerIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(open, resumed));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());

        List<LockerCollectionSuspensionOverviewDTO> overview = collectionSuspensionService.getLockerOverview();

        assertEquals(2, overview.size());
        LockerCollectionSuspensionOverviewDTO first = overview.get(0);
        assertEquals(2L, first.getLockerId());
        assertTrue(first.getSuspended());
        assertTrue(first.getOverdue(), "停收中且已过预计恢复时间，一览应标记逾时");
        assertEquals(1, first.getOpenRecordCount());
        assertEquals(2, first.getTotalRecordCount());

        LockerCollectionSuspensionOverviewDTO second = overview.get(1);
        assertEquals(1L, second.getLockerId());
        assertFalse(second.getSuspended());
        assertEquals(0, second.getOpenRecordCount());
    }
}
