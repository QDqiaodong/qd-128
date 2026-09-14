package com.example.locker.service;

import com.example.locker.dto.DoorAlarmCloseRequest;
import com.example.locker.dto.DoorAlarmCreateRequest;
import com.example.locker.dto.DoorAlarmRecordDTO;
import com.example.locker.dto.LockerDoorAlarmOverviewDTO;
import com.example.locker.entity.DoorAlarmRecord;
import com.example.locker.entity.Locker;
import com.example.locker.enums.DoorAlarmStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.DoorAlarmRecordRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoorAlarmServiceTest {

    @Mock
    private DoorAlarmRecordRepository doorAlarmRecordRepository;

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private DoorAlarmService doorAlarmService;

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

    private DoorAlarmCreateRequest validRequest() {
        DoorAlarmCreateRequest request = new DoorAlarmCreateRequest();
        request.setLockerId(1L);
        request.setDoorOpenTime(LocalDateTime.now().minusMinutes(30));
        request.setReporter("物业巡柜-老周");
        return request;
    }

    @Test
    void reportAlarmPersistsOpenAlarmWithDefaultThreshold() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(doorAlarmRecordRepository.existsByLockerIdAndStatus(1L, DoorAlarmStatus.OPEN)).thenReturn(false);
        when(doorAlarmRecordRepository.existsByAlarmNo(anyString())).thenReturn(false);
        when(doorAlarmRecordRepository.save(any(DoorAlarmRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoorAlarmRecordDTO dto = doorAlarmService.reportAlarm(validRequest());

        assertEquals(DoorAlarmStatus.OPEN, dto.getStatus());
        assertTrue(dto.getOpen(), "登记后应为未处理");
        assertEquals(DoorAlarmService.DEFAULT_THRESHOLD_MINUTES, dto.getThresholdMinutes(),
                "未指定约定分钟时应按系统约定值");
        assertEquals("物业巡柜-老周", dto.getReporter());
        assertNotNull(dto.getAlarmNo());
        verify(doorAlarmRecordRepository, times(1)).save(any(DoorAlarmRecord.class));
    }

    @Test
    void reportAlarmRejectsMissingRequiredFields() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        DoorAlarmCreateRequest noLocker = validRequest();
        noLocker.setLockerId(null);
        assertThrows(IllegalArgumentException.class, () -> doorAlarmService.reportAlarm(noLocker));

        DoorAlarmCreateRequest noReporter = validRequest();
        noReporter.setReporter("  ");
        assertThrows(IllegalArgumentException.class, () -> doorAlarmService.reportAlarm(noReporter));

        assertThrows(IllegalArgumentException.class, () -> doorAlarmService.reportAlarm(null));

        // 任何校验失败都不能落库，避免写出半条台账
        verify(doorAlarmRecordRepository, never()).save(any(DoorAlarmRecord.class));
    }

    @Test
    void reportAlarmRejectsFutureDoorOpenTime() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        DoorAlarmCreateRequest request = validRequest();
        request.setDoorOpenTime(LocalDateTime.now().plusMinutes(5));
        assertThrows(IllegalArgumentException.class, () -> doorAlarmService.reportAlarm(request));
        verify(doorAlarmRecordRepository, never()).save(any(DoorAlarmRecord.class));
    }

    @Test
    void reportAlarmRejectsInvalidThreshold() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        DoorAlarmCreateRequest request = validRequest();
        request.setThresholdMinutes(0);
        assertThrows(IllegalArgumentException.class, () -> doorAlarmService.reportAlarm(request));
        verify(doorAlarmRecordRepository, never()).save(any(DoorAlarmRecord.class));
    }

    @Test
    void reportAlarmRejectsLockerAlreadyHasOpenAlarm() {
        // 同一柜已有未处理告警时不能重复登记，先确认关闭后再登记
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(doorAlarmRecordRepository.existsByLockerIdAndStatus(1L, DoorAlarmStatus.OPEN)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> doorAlarmService.reportAlarm(validRequest()));
        verify(doorAlarmRecordRepository, never()).save(any(DoorAlarmRecord.class));
    }

    @Test
    void closeAlarmClosesSameRecord() {
        // 确认已关闭：同一条台账记录状态流转为已关闭，不新增也不删除，
        // 台账按未处理/已关闭筛选找到的都是这同一条
        DoorAlarmRecord record = new DoorAlarmRecord();
        record.setId(7L);
        record.setAlarmNo("MJ20260913001");
        record.setLockerId(1L);
        record.setDoorOpenTime(LocalDateTime.now().minusMinutes(30));
        record.setThresholdMinutes(10);
        record.setReporter("物业巡柜-老周");
        record.setStatus(DoorAlarmStatus.OPEN);
        when(doorAlarmRecordRepository.findById(7L)).thenReturn(Optional.of(record));
        when(doorAlarmRecordRepository.save(any(DoorAlarmRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoorAlarmRecordDTO dto = doorAlarmService.closeAlarm(7L, new DoorAlarmCloseRequest());

        assertEquals(7L, dto.getId(), "确认关闭后仍是同一条台账记录");
        assertEquals("MJ20260913001", dto.getAlarmNo());
        assertEquals(DoorAlarmStatus.CLOSED, dto.getStatus());
        assertFalse(dto.getOpen(), "确认关闭后不再是未处理");
        assertFalse(dto.getOvertime(), "已关闭记录不再算超时");
        assertEquals("系统管理员", dto.getCloseOperator(), "关闭人留空默认系统管理员");
        assertNotNull(dto.getCloseTime());
    }

    @Test
    void closeAlarmRejectsAlreadyClosed() {
        DoorAlarmRecord record = new DoorAlarmRecord();
        record.setId(7L);
        record.setLockerId(1L);
        record.setStatus(DoorAlarmStatus.CLOSED);
        when(doorAlarmRecordRepository.findById(7L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class,
                () -> doorAlarmService.closeAlarm(7L, new DoorAlarmCloseRequest()));
        verify(doorAlarmRecordRepository, never()).save(any(DoorAlarmRecord.class));
    }

    @Test
    void overtimeDerivedFromAgreedMinutes() {
        // 超过约定分钟仍未关严：未处理且发现时间+约定分钟已过 → 超时
        DoorAlarmRecord overtimeRecord = new DoorAlarmRecord();
        overtimeRecord.setId(1L);
        overtimeRecord.setLockerId(1L);
        overtimeRecord.setDoorOpenTime(LocalDateTime.now().minusMinutes(30));
        overtimeRecord.setThresholdMinutes(10);
        overtimeRecord.setStatus(DoorAlarmStatus.OPEN);
        when(doorAlarmRecordRepository.findById(1L)).thenReturn(Optional.of(overtimeRecord));
        assertTrue(doorAlarmService.getAlarm(1L).getOvertime(), "超过约定分钟仍未关严必须标记超时");

        // 未超约定分钟：不超时
        DoorAlarmRecord withinRecord = new DoorAlarmRecord();
        withinRecord.setId(2L);
        withinRecord.setLockerId(1L);
        withinRecord.setDoorOpenTime(LocalDateTime.now().minusMinutes(5));
        withinRecord.setThresholdMinutes(10);
        withinRecord.setStatus(DoorAlarmStatus.OPEN);
        when(doorAlarmRecordRepository.findById(2L)).thenReturn(Optional.of(withinRecord));
        assertFalse(doorAlarmService.getAlarm(2L).getOvertime(), "未超约定分钟不应标记超时");
    }

    @Test
    void lockerOverviewMarksDoorAjarLockers() {
        // 全部柜体列出：柜门未关的柜体置顶并标记，未处理条数实时推导
        Locker other = new Locker();
        other.setId(2L);
        other.setLockerNo("KDG-002");
        other.setBuildingId(1L);
        other.setUnitId(1L);
        other.setStatus(LockerStatus.ACTIVE);
        when(lockerRepository.findAll(any(Sort.class))).thenReturn(List.of(locker, other));

        DoorAlarmRecord open = new DoorAlarmRecord();
        open.setLockerId(2L);
        open.setStatus(DoorAlarmStatus.OPEN);
        open.setDoorOpenTime(LocalDateTime.now().minusHours(1));
        open.setThresholdMinutes(10);
        DoorAlarmRecord closed = new DoorAlarmRecord();
        closed.setLockerId(2L);
        closed.setStatus(DoorAlarmStatus.CLOSED);
        closed.setDoorOpenTime(LocalDateTime.now().minusDays(3));
        closed.setThresholdMinutes(10);
        when(doorAlarmRecordRepository.findByLockerIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(open, closed));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());

        List<LockerDoorAlarmOverviewDTO> overview = doorAlarmService.getLockerOverview();

        assertEquals(2, overview.size());
        LockerDoorAlarmOverviewDTO first = overview.get(0);
        assertEquals(2L, first.getLockerId());
        assertTrue(first.getDoorAjar());
        assertTrue(first.getOvertime(), "未处理且已超约定分钟，一览应标记超时");
        assertEquals(1, first.getOpenAlarmCount());
        assertEquals(2, first.getTotalAlarmCount());

        LockerDoorAlarmOverviewDTO second = overview.get(1);
        assertEquals(1L, second.getLockerId());
        assertFalse(second.getDoorAjar());
        assertEquals(0, second.getOpenAlarmCount());
    }
}
