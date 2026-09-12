package com.example.locker.service;

import com.example.locker.dto.KeyBorrowCreateRequest;
import com.example.locker.dto.KeyBorrowRecordDTO;
import com.example.locker.dto.KeyBorrowReturnRequest;
import com.example.locker.dto.LockerKeyBorrowOverviewDTO;
import com.example.locker.entity.KeyBorrowRecord;
import com.example.locker.entity.Locker;
import com.example.locker.enums.KeyBorrowStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.KeyBorrowRecordRepository;
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
class KeyBorrowServiceTest {

    @Mock
    private KeyBorrowRecordRepository keyBorrowRecordRepository;

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private KeyBorrowService keyBorrowService;

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

    private KeyBorrowCreateRequest validRequest() {
        KeyBorrowCreateRequest request = new KeyBorrowCreateRequest();
        request.setLockerId(1L);
        request.setBorrower("王维修");
        request.setReason("柜门检修");
        request.setBorrowTime(LocalDateTime.now().minusHours(2));
        request.setExpectedReturnTime(LocalDateTime.now().plusHours(4));
        return request;
    }

    @Test
    void createRecordPersistsSingleOnLoanRecord() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(keyBorrowRecordRepository.existsByLockerIdAndStatus(1L, KeyBorrowStatus.ON_LOAN))
                .thenReturn(false);
        when(keyBorrowRecordRepository.existsByRecordNo(anyString())).thenReturn(false);
        when(keyBorrowRecordRepository.save(any(KeyBorrowRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KeyBorrowRecordDTO dto = keyBorrowService.createRecord(validRequest());

        assertEquals(KeyBorrowStatus.ON_LOAN, dto.getStatus());
        assertTrue(dto.getOnLoan(), "借用中标记应为 true");
        assertEquals("王维修", dto.getBorrower());
        assertEquals("柜门检修", dto.getReason());
        assertNotNull(dto.getRecordNo());
        verify(keyBorrowRecordRepository, times(1)).save(any(KeyBorrowRecord.class));
    }

    @Test
    void createRecordAllowsPermanentlyDisabledLockerForHistory() {
        // 永久停用柜仍可补登历史借用
        locker.setStatus(LockerStatus.PERMANENTLY_DISABLED);
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(keyBorrowRecordRepository.existsByLockerIdAndStatus(1L, KeyBorrowStatus.ON_LOAN))
                .thenReturn(false);
        when(keyBorrowRecordRepository.existsByRecordNo(anyString())).thenReturn(false);
        when(keyBorrowRecordRepository.save(any(KeyBorrowRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KeyBorrowCreateRequest request = validRequest();
        request.setBorrowTime(LocalDateTime.now().minusDays(30));
        request.setExpectedReturnTime(LocalDateTime.now().minusDays(29));

        KeyBorrowRecordDTO dto = keyBorrowService.createRecord(request);
        assertEquals(KeyBorrowStatus.ON_LOAN, dto.getStatus());
        verify(keyBorrowRecordRepository).save(any(KeyBorrowRecord.class));
    }

    @Test
    void createRecordRejectsWhenKeyStillOnLoan() {
        // 同一柜钥匙未还清前不能再借出
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));
        when(keyBorrowRecordRepository.existsByLockerIdAndStatus(1L, KeyBorrowStatus.ON_LOAN))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> keyBorrowService.createRecord(validRequest()));
        verify(keyBorrowRecordRepository, never()).save(any(KeyBorrowRecord.class));
    }

    @Test
    void createRecordRejectsMissingRequiredFields() {
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        KeyBorrowCreateRequest noBorrower = validRequest();
        noBorrower.setBorrower(" ");
        assertThrows(IllegalArgumentException.class, () -> keyBorrowService.createRecord(noBorrower));

        KeyBorrowCreateRequest noReason = validRequest();
        noReason.setReason(null);
        assertThrows(IllegalArgumentException.class, () -> keyBorrowService.createRecord(noReason));

        KeyBorrowCreateRequest noBorrowTime = validRequest();
        noBorrowTime.setBorrowTime(null);
        assertThrows(IllegalArgumentException.class, () -> keyBorrowService.createRecord(noBorrowTime));

        KeyBorrowCreateRequest futureBorrowTime = validRequest();
        futureBorrowTime.setBorrowTime(LocalDateTime.now().plusHours(1));
        assertThrows(IllegalArgumentException.class, () -> keyBorrowService.createRecord(futureBorrowTime));

        KeyBorrowCreateRequest noExpectedReturn = validRequest();
        noExpectedReturn.setExpectedReturnTime(null);
        assertThrows(IllegalArgumentException.class, () -> keyBorrowService.createRecord(noExpectedReturn));

        KeyBorrowCreateRequest expectedBeforeBorrow = validRequest();
        expectedBeforeBorrow.setExpectedReturnTime(expectedBeforeBorrow.getBorrowTime().minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> keyBorrowService.createRecord(expectedBeforeBorrow));

        // 任何校验失败都不能落库，避免写出半条台账
        verify(keyBorrowRecordRepository, never()).save(any(KeyBorrowRecord.class));
    }

    @Test
    void returnRecordRequiresReturner() {
        KeyBorrowReturnRequest request = new KeyBorrowReturnRequest();
        request.setReturner("  ");
        assertThrows(IllegalArgumentException.class,
                () -> keyBorrowService.returnRecord(1L, request));
        verify(keyBorrowRecordRepository, never()).save(any(KeyBorrowRecord.class));
    }

    @Test
    void returnRecordSetsReturnerAndTime() {
        KeyBorrowRecord record = new KeyBorrowRecord();
        record.setId(1L);
        record.setLockerId(1L);
        record.setStatus(KeyBorrowStatus.ON_LOAN);
        record.setBorrowTime(LocalDateTime.now().minusHours(3));
        when(keyBorrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(keyBorrowRecordRepository.save(any(KeyBorrowRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerRepository.findById(1L)).thenReturn(Optional.of(locker));

        KeyBorrowReturnRequest request = new KeyBorrowReturnRequest();
        request.setReturner("李管家");
        KeyBorrowRecordDTO dto = keyBorrowService.returnRecord(1L, request);

        assertEquals(KeyBorrowStatus.RETURNED, dto.getStatus());
        assertFalse(dto.getOnLoan());
        assertEquals("李管家", dto.getReturner());
        assertNotNull(dto.getReturnTime());
    }

    @Test
    void returnRecordRejectsAlreadyReturned() {
        KeyBorrowRecord record = new KeyBorrowRecord();
        record.setId(1L);
        record.setStatus(KeyBorrowStatus.RETURNED);
        when(keyBorrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        KeyBorrowReturnRequest request = new KeyBorrowReturnRequest();
        request.setReturner("李管家");
        assertThrows(IllegalArgumentException.class,
                () -> keyBorrowService.returnRecord(1L, request));
        verify(keyBorrowRecordRepository, never()).save(any(KeyBorrowRecord.class));
    }

    @Test
    void returnRecordRejectsReturnTimeBeforeBorrowTime() {
        KeyBorrowRecord record = new KeyBorrowRecord();
        record.setId(1L);
        record.setLockerId(1L);
        record.setStatus(KeyBorrowStatus.ON_LOAN);
        record.setBorrowTime(LocalDateTime.now().minusHours(2));
        when(keyBorrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        KeyBorrowReturnRequest request = new KeyBorrowReturnRequest();
        request.setReturner("李管家");
        request.setReturnTime(LocalDateTime.now().minusHours(5));
        assertThrows(IllegalArgumentException.class,
                () -> keyBorrowService.returnRecord(1L, request));
        verify(keyBorrowRecordRepository, never()).save(any(KeyBorrowRecord.class));
    }

    @Test
    void lockerOverviewMarksOnLoanLockers() {
        // 全部柜体列出：借用中的柜体置顶并标记，未还条数实时推导
        Locker other = new Locker();
        other.setId(2L);
        other.setLockerNo("KDG-002");
        other.setBuildingId(1L);
        other.setUnitId(1L);
        other.setStatus(LockerStatus.ACTIVE);
        when(lockerRepository.findAll(any(Sort.class))).thenReturn(List.of(locker, other));

        KeyBorrowRecord open = new KeyBorrowRecord();
        open.setLockerId(2L);
        open.setStatus(KeyBorrowStatus.ON_LOAN);
        open.setBorrowTime(LocalDateTime.now().minusHours(1));
        when(keyBorrowRecordRepository.findByLockerIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(open));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());

        List<LockerKeyBorrowOverviewDTO> overview = keyBorrowService.getLockerOverview();

        assertEquals(2, overview.size());
        LockerKeyBorrowOverviewDTO first = overview.get(0);
        assertEquals(2L, first.getLockerId());
        assertTrue(first.getOnLoan());
        assertEquals(1, first.getOpenRecordCount());

        LockerKeyBorrowOverviewDTO second = overview.get(1);
        assertEquals(1L, second.getLockerId());
        assertFalse(second.getOnLoan());
        assertEquals(0, second.getOpenRecordCount());
    }
}
