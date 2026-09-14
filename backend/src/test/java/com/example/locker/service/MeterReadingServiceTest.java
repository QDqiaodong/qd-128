package com.example.locker.service;

import com.example.locker.dto.LockerMeterReadingOverviewDTO;
import com.example.locker.dto.MeterReadingCreateRequest;
import com.example.locker.dto.MeterReadingRecordDTO;
import com.example.locker.dto.MeterReadingVoidRequest;
import com.example.locker.entity.Locker;
import com.example.locker.entity.MeterReadingRecord;
import com.example.locker.enums.LockerStatus;
import com.example.locker.enums.MeterReadingStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.MeterReadingRecordRepository;
import com.example.locker.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeterReadingServiceTest {

    @Mock
    private MeterReadingRecordRepository meterReadingRecordRepository;

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private MeterReadingService meterReadingService;

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

    private MeterReadingCreateRequest validRequest() {
        MeterReadingCreateRequest request = new MeterReadingCreateRequest();
        request.setLockerId(1L);
        request.setReadingValue(new BigDecimal("1280.50"));
        request.setReader("李抄表");
        request.setReadingTime(LocalDateTime.now().minusHours(2));
        return request;
    }

    private String currentPeriod() {
        return YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String previousPeriod() {
        return YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private MeterReadingRecord activeRecord(Long id, Long lockerId, String periodMonth,
                                            String readingValue, String reader) {
        MeterReadingRecord record = new MeterReadingRecord();
        record.setId(id);
        record.setLockerId(lockerId);
        record.setPeriodMonth(periodMonth);
        record.setReadingValue(new BigDecimal(readingValue));
        record.setReader(reader);
        record.setReadingTime(LocalDateTime.now().minusDays(5));
        record.setStatus(MeterReadingStatus.ACTIVE);
        return record;
    }

    @Test
    void createRecordPersistsSingleActiveRecord() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(meterReadingRecordRepository.existsByLockerIdAndPeriodMonthAndStatus(
                1L, currentPeriod(), MeterReadingStatus.ACTIVE)).thenReturn(false);
        when(meterReadingRecordRepository.existsByRecordNo(anyString())).thenReturn(false);
        when(meterReadingRecordRepository.save(any(MeterReadingRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MeterReadingRecordDTO dto = meterReadingService.createRecord(validRequest());

        assertEquals(MeterReadingStatus.ACTIVE, dto.getStatus());
        assertTrue(dto.getActive(), "有效标记应为 true");
        assertTrue(dto.getCurrentMonth(), "本月账期的有效单应标记为本月单");
        assertEquals(currentPeriod(), dto.getPeriodMonth(), "账期应由抄表时间推导为当前自然月");
        assertEquals(new BigDecimal("1280.50"), dto.getReadingValue());
        assertEquals("李抄表", dto.getReader());
        assertNotNull(dto.getRecordNo());
        verify(meterReadingRecordRepository, times(1)).save(any(MeterReadingRecord.class));
    }

    @Test
    void createRecordDerivesPeriodFromReadingTime() {
        // 补登历史月份：账期跟随抄表时间所在自然月
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(meterReadingRecordRepository.existsByLockerIdAndPeriodMonthAndStatus(
                any(), anyString(), any())).thenReturn(false);
        when(meterReadingRecordRepository.existsByRecordNo(anyString())).thenReturn(false);
        when(meterReadingRecordRepository.save(any(MeterReadingRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MeterReadingCreateRequest request = validRequest();
        request.setReadingTime(LocalDateTime.now().minusMonths(1));
        MeterReadingRecordDTO dto = meterReadingService.createRecord(request);

        String expected = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        assertEquals(expected, dto.getPeriodMonth());
        assertFalse(dto.getCurrentMonth(), "上月账期的单不应计入本月已抄");
        verify(meterReadingRecordRepository).save(any(MeterReadingRecord.class));
    }

    @Test
    void createRecordRejectsSecondActiveRecordInSameMonth() {
        // 同一柜同一自然月不能同时挂两张未作废抄表单
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(meterReadingRecordRepository.existsByLockerIdAndPeriodMonthAndStatus(
                1L, currentPeriod(), MeterReadingStatus.ACTIVE)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> meterReadingService.createRecord(validRequest()));
        assertTrue(ex.getMessage().contains("同一柜同一自然月"), "应提示同月已有有效单");
        verify(meterReadingRecordRepository, never()).save(any(MeterReadingRecord.class));
    }

    @Test
    void createRecordRejectsMissingRequiredFields() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        MeterReadingCreateRequest noValue = validRequest();
        noValue.setReadingValue(null);
        assertThrows(IllegalArgumentException.class, () -> meterReadingService.createRecord(noValue));

        MeterReadingCreateRequest negativeValue = validRequest();
        negativeValue.setReadingValue(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> meterReadingService.createRecord(negativeValue));

        MeterReadingCreateRequest noReader = validRequest();
        noReader.setReader("  ");
        assertThrows(IllegalArgumentException.class, () -> meterReadingService.createRecord(noReader));

        MeterReadingCreateRequest noTime = validRequest();
        noTime.setReadingTime(null);
        assertThrows(IllegalArgumentException.class, () -> meterReadingService.createRecord(noTime));

        MeterReadingCreateRequest futureTime = validRequest();
        futureTime.setReadingTime(LocalDateTime.now().plusHours(1));
        assertThrows(IllegalArgumentException.class, () -> meterReadingService.createRecord(futureTime));

        // 任何校验失败都不能落库，避免写出半张抄表单
        verify(meterReadingRecordRepository, never()).save(any(MeterReadingRecord.class));
    }

    @Test
    void voidRecordRequiresReason() {
        // 作废必须写原因
        MeterReadingVoidRequest request = new MeterReadingVoidRequest();
        request.setVoidReason("  ");
        assertThrows(IllegalArgumentException.class,
                () -> meterReadingService.voidRecord(1L, request));
        verify(meterReadingRecordRepository, never()).save(any(MeterReadingRecord.class));
    }

    @Test
    void voidRecordSetsVoidFields() {
        MeterReadingRecord record = new MeterReadingRecord();
        record.setId(1L);
        record.setLockerId(1L);
        record.setPeriodMonth(currentPeriod());
        record.setStatus(MeterReadingStatus.ACTIVE);
        when(meterReadingRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(meterReadingRecordRepository.save(any(MeterReadingRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        MeterReadingVoidRequest request = new MeterReadingVoidRequest();
        request.setVoidReason("读数录入错误，重新抄表");
        MeterReadingRecordDTO dto = meterReadingService.voidRecord(1L, request);

        assertEquals(MeterReadingStatus.VOIDED, dto.getStatus());
        assertFalse(dto.getActive(), "作废后不再占用该月有效单额度");
        assertFalse(dto.getCurrentMonth(), "作废单不计入本月已抄");
        assertEquals("读数录入错误，重新抄表", dto.getVoidReason());
        assertEquals("系统管理员", dto.getVoidOperator(), "作废人留空默认系统管理员");
        assertNotNull(dto.getVoidTime());
    }

    @Test
    void voidRecordRejectsAlreadyVoided() {
        MeterReadingRecord record = new MeterReadingRecord();
        record.setId(1L);
        record.setStatus(MeterReadingStatus.VOIDED);
        when(meterReadingRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        MeterReadingVoidRequest request = new MeterReadingVoidRequest();
        request.setVoidReason("重复作废");
        assertThrows(IllegalArgumentException.class,
                () -> meterReadingService.voidRecord(1L, request));
        verify(meterReadingRecordRepository, never()).save(any(MeterReadingRecord.class));
    }

    @Test
    void createRecordRejectsReadingNotGreaterThanLast() {
        // 上次有效抄表 1280.50 kWh，本次登记读数必须更大，等于或小于都拦截，不落库
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(meterReadingRecordRepository.existsByLockerIdAndPeriodMonthAndStatus(
                1L, currentPeriod(), MeterReadingStatus.ACTIVE)).thenReturn(false);
        when(meterReadingRecordRepository
                .findFirstByLockerIdAndPeriodMonthLessThanAndStatusOrderByPeriodMonthDescIdDesc(
                        1L, currentPeriod(), MeterReadingStatus.ACTIVE))
                .thenReturn(Optional.of(activeRecord(9L, 1L, previousPeriod(), "1280.50", "李抄表")));

        MeterReadingCreateRequest equal = validRequest();
        equal.setReadingValue(new BigDecimal("1280.50"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> meterReadingService.createRecord(equal));
        assertTrue(ex.getMessage().contains("必须大于上次读数"), "应提示读数必须大于上次读数");

        MeterReadingCreateRequest lower = validRequest();
        lower.setReadingValue(new BigDecimal("1200"));
        assertThrows(IllegalArgumentException.class, () -> meterReadingService.createRecord(lower));

        verify(meterReadingRecordRepository, never()).save(any(MeterReadingRecord.class));
    }

    @Test
    void createRecordAllowsReadingGreaterThanLast() {
        // 本次读数大于上次读数，正常落库
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(meterReadingRecordRepository.existsByLockerIdAndPeriodMonthAndStatus(
                1L, currentPeriod(), MeterReadingStatus.ACTIVE)).thenReturn(false);
        when(meterReadingRecordRepository
                .findFirstByLockerIdAndPeriodMonthLessThanAndStatusOrderByPeriodMonthDescIdDesc(
                        1L, currentPeriod(), MeterReadingStatus.ACTIVE))
                .thenReturn(Optional.of(activeRecord(9L, 1L, previousPeriod(), "1280.50", "李抄表")));
        when(meterReadingRecordRepository.existsByRecordNo(anyString())).thenReturn(false);
        when(meterReadingRecordRepository.save(any(MeterReadingRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MeterReadingCreateRequest request = validRequest();
        request.setReadingValue(new BigDecimal("1280.51"));
        MeterReadingRecordDTO dto = meterReadingService.createRecord(request);

        assertEquals(new BigDecimal("1280.51"), dto.getReadingValue());
        verify(meterReadingRecordRepository, times(1)).save(any(MeterReadingRecord.class));
    }

    @Test
    void lockerOverviewMarksReadAndUnreadLockers() {
        // 已抄/未抄标记与本月读数由有效抄表单实时推导，未抄的柜体排前面
        Locker other = new Locker();
        other.setId(2L);
        other.setLockerNo("KDG-002");
        other.setBuildingId(1L);
        other.setUnitId(1L);
        other.setStatus(LockerStatus.ACTIVE);
        when(lockerRepository.findAll(any(Sort.class))).thenReturn(List.of(locker, other));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());

        MeterReadingRecord active = new MeterReadingRecord();
        active.setId(10L);
        active.setLockerId(1L);
        active.setPeriodMonth(currentPeriod());
        active.setReadingValue(new BigDecimal("1280.50"));
        active.setReader("李抄表");
        active.setReadingTime(LocalDateTime.now().minusHours(3));
        active.setStatus(MeterReadingStatus.ACTIVE);
        when(meterReadingRecordRepository.findByLockerIdInAndPeriodMonthAndStatus(
                List.of(1L, 2L), currentPeriod(), MeterReadingStatus.ACTIVE))
                .thenReturn(List.of(active));

        List<LockerMeterReadingOverviewDTO> overview = meterReadingService.getLockerOverview(null);

        assertEquals(2, overview.size());
        LockerMeterReadingOverviewDTO first = overview.get(0);
        assertEquals(2L, first.getLockerId(), "未抄的柜体应排在前面");
        assertFalse(first.getRead());
        assertNull(first.getReadingValue());

        LockerMeterReadingOverviewDTO second = overview.get(1);
        assertEquals(1L, second.getLockerId());
        assertTrue(second.getRead(), "存在本月有效单的柜体应标记已抄");
        assertEquals(new BigDecimal("1280.50"), second.getReadingValue());
        assertEquals("李抄表", second.getReader());
        assertEquals(currentPeriod(), second.getPeriodMonth());
    }

    @Test
    void lockerOverviewCarriesLastReadingForReadAndUnreadRows() {
        // 已抄、未抄的行都要带出本账期之前最近一次有效抄表；从未抄过的柜体三项均为空
        Locker second = new Locker();
        second.setId(2L);
        second.setLockerNo("KDG-002");
        second.setBuildingId(1L);
        second.setUnitId(1L);
        second.setStatus(LockerStatus.ACTIVE);
        Locker third = new Locker();
        third.setId(3L);
        third.setLockerNo("KDG-003");
        third.setBuildingId(1L);
        third.setUnitId(1L);
        third.setStatus(LockerStatus.ACTIVE);
        when(lockerRepository.findAll(any(Sort.class))).thenReturn(List.of(locker, second, third));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());

        // locker1 本月已抄；locker1、locker2 有历史有效单；locker3 从未抄过
        when(meterReadingRecordRepository.findByLockerIdInAndPeriodMonthAndStatus(
                List.of(1L, 2L, 3L), currentPeriod(), MeterReadingStatus.ACTIVE))
                .thenReturn(List.of(activeRecord(10L, 1L, currentPeriod(), "1280.50", "李抄表")));
        when(meterReadingRecordRepository.findByLockerIdInAndPeriodMonthLessThanAndStatus(
                List.of(1L, 2L, 3L), currentPeriod(), MeterReadingStatus.ACTIVE))
                .thenReturn(List.of(
                        activeRecord(8L, 1L, previousPeriod(), "1000", "王历史"),
                        activeRecord(9L, 2L, previousPeriod(), "2000", "赵历史")));

        List<LockerMeterReadingOverviewDTO> overview = meterReadingService.getLockerOverview(null);

        assertEquals(3, overview.size());
        // 未抄的排前面：KDG-002、KDG-003，已抄的 KDG-001 在最后
        LockerMeterReadingOverviewDTO unreadWithHistory = overview.get(0);
        assertEquals(2L, unreadWithHistory.getLockerId());
        assertFalse(unreadWithHistory.getRead());
        assertEquals(new BigDecimal("2000"), unreadWithHistory.getLastReadingValue(),
                "未抄的行也要带出上次读数");
        assertEquals("赵历史", unreadWithHistory.getLastReader());
        assertNotNull(unreadWithHistory.getLastReadingTime());

        LockerMeterReadingOverviewDTO neverRead = overview.get(1);
        assertEquals(3L, neverRead.getLockerId());
        assertFalse(neverRead.getRead());
        assertNull(neverRead.getLastReadingValue(), "从未抄过的柜体上次读数为空，前端显示尚未抄过");
        assertNull(neverRead.getLastReader());
        assertNull(neverRead.getLastReadingTime());

        LockerMeterReadingOverviewDTO readRow = overview.get(2);
        assertEquals(1L, readRow.getLockerId());
        assertTrue(readRow.getRead());
        assertEquals(new BigDecimal("1280.50"), readRow.getReadingValue());
        assertEquals(new BigDecimal("1000"), readRow.getLastReadingValue(),
                "已抄的行上次读数应为本月这张单之前的最近一次抄表");
        assertEquals("王历史", readRow.getLastReader());
        assertNotNull(readRow.getLastReadingTime());
    }
}
