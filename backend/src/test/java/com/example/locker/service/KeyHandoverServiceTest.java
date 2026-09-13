package com.example.locker.service;

import com.example.locker.dto.KeyHandoverCreateRequest;
import com.example.locker.dto.KeyHandoverDTO;
import com.example.locker.dto.KeyHandoverPendingItemDTO;
import com.example.locker.entity.KeyBorrowRecord;
import com.example.locker.entity.KeyHandover;
import com.example.locker.entity.KeyHandoverItem;
import com.example.locker.entity.Locker;
import com.example.locker.enums.KeyBorrowStatus;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.KeyBorrowRecordRepository;
import com.example.locker.repository.KeyHandoverItemRepository;
import com.example.locker.repository.KeyHandoverRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeyHandoverServiceTest {

    @Mock
    private KeyHandoverRepository keyHandoverRepository;
    @Mock
    private KeyHandoverItemRepository keyHandoverItemRepository;
    @Mock
    private KeyBorrowRecordRepository keyBorrowRecordRepository;
    @Mock
    private LockerRepository lockerRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private KeyHandoverService service;

    private Locker locker;
    private KeyBorrowRecord r1;
    private KeyBorrowRecord r2;

    @BeforeEach
    void setUp() {
        locker = new Locker();
        locker.setId(10L);
        locker.setLockerNo("KDG-001");
        locker.setBuildingId(1L);
        locker.setUnitId(1L);
        locker.setStatus(LockerStatus.ACTIVE);

        r1 = openRecord(1L, 10L, "王维修");
        r2 = openRecord(2L, 11L, "李管家");
    }

    private KeyBorrowRecord openRecord(Long id, Long lockerId, String borrower) {
        KeyBorrowRecord r = new KeyBorrowRecord();
        r.setId(id);
        r.setRecordNo("JY" + id);
        r.setLockerId(lockerId);
        r.setBorrower(borrower);
        r.setReason("柜门检修");
        r.setBorrowTime(LocalDateTime.now().minusHours(2));
        r.setExpectedReturnTime(LocalDateTime.now().plusHours(4));
        r.setStatus(KeyBorrowStatus.ON_LOAN);
        return r;
    }

    private KeyHandoverCreateRequest validRequest(List<Long> ids) {
        KeyHandoverCreateRequest req = new KeyHandoverCreateRequest();
        req.setHandoverFrom("早班-张三");
        req.setHandoverTo("晚班-李四");
        req.setHandoverNote("两把钥匙均在前台抽屉，注意 KDG-001 已逾期");
        req.setRecordIds(ids);
        return req;
    }

    @Test
    void getPendingItemsListsAllOpenRecords() {
        when(keyBorrowRecordRepository.findByStatusOrderByCreateTimeDesc(KeyBorrowStatus.ON_LOAN))
                .thenReturn(List.of(r1, r2));
        when(lockerRepository.findAllById(any())).thenReturn(List.of(locker));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());

        List<KeyHandoverPendingItemDTO> items = service.getPendingItems();
        assertEquals(2, items.size(), "待点名清单应包含全部未还记录");
        assertEquals(1L, items.get(0).getRecordId());
        assertFalse(items.get(0).getOverdue(), "预计归还未到，不标记逾期");
    }

    @Test
    void getPendingItemsEmptyWhenNoOpenRecord() {
        when(keyBorrowRecordRepository.findByStatusOrderByCreateTimeDesc(KeyBorrowStatus.ON_LOAN))
                .thenReturn(Collections.emptyList());
        assertTrue(service.getPendingItems().isEmpty());
        verify(lockerRepository, never()).findAllById(any());
    }

    @Test
    void submitPersistsWhenAllOpenRecordsChecked() {
        when(keyBorrowRecordRepository.findByStatusOrderByCreateTimeDesc(KeyBorrowStatus.ON_LOAN))
                .thenReturn(List.of(r1, r2));
        when(keyHandoverRepository.existsByHandoverNo(anyString())).thenReturn(false);
        when(keyHandoverRepository.save(any(KeyHandover.class))).thenAnswer(inv -> {
            KeyHandover h = inv.getArgument(0);
            h.setId(100L);
            return h;
        });
        when(lockerRepository.findAllById(any())).thenReturn(List.of(locker));
        when(buildingRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(keyBorrowRecordRepository.findAllById(any())).thenReturn(List.of(r1, r2));

        KeyHandoverDTO dto = service.submitHandover(validRequest(List.of(1L, 2L)));

        assertEquals(2, dto.getItemCount(), "交接条数应等于全部未还条数");
        assertEquals(2, dto.getItems().size());
        // 交接不改借用状态，被点名记录仍是借用中
        assertTrue(dto.getItems().stream().allMatch(i -> Boolean.TRUE.equals(i.getOnLoan())));
        // 明细按全部未还记录逐一点名落库
        verify(keyHandoverItemRepository, times(1)).saveAll(argThat((List<KeyHandoverItem> list) ->
                list != null && list.size() == 2
                        && list.stream().allMatch(i -> i.getHandoverId() == 100L)));
        // 借用台账本身不做任何状态写回
        verify(keyBorrowRecordRepository, never()).save(any(KeyBorrowRecord.class));
    }

    @Test
    void submitRejectsWhenNotAllOpenRecordsChecked() {
        when(keyBorrowRecordRepository.findByStatusOrderByCreateTimeDesc(KeyBorrowStatus.ON_LOAN))
                .thenReturn(List.of(r1, r2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitHandover(validRequest(List.of(1L))));
        assertTrue(ex.getMessage().contains("未点名"));
        verify(keyHandoverRepository, never()).save(any(KeyHandover.class));
        verify(keyHandoverItemRepository, never()).saveAll(any());
    }

    @Test
    void submitRejectsCheckedRecordNotOpen() {
        // 勾选名单里混入已归还/不存在的单：勾多也不能交班
        when(keyBorrowRecordRepository.findByStatusOrderByCreateTimeDesc(KeyBorrowStatus.ON_LOAN))
                .thenReturn(List.of(r1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitHandover(validRequest(List.of(1L, 999L))));
        assertTrue(ex.getMessage().contains("已归还或不存在"));
        verify(keyHandoverRepository, never()).save(any(KeyHandover.class));
    }

    @Test
    void submitRejectsDuplicateSelection() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitHandover(validRequest(List.of(1L, 1L))));
        verify(keyBorrowRecordRepository, never())
                .findByStatusOrderByCreateTimeDesc(any());
        verify(keyHandoverRepository, never()).save(any(KeyHandover.class));
    }

    @Test
    void submitRejectsEmptySelection() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitHandover(validRequest(new ArrayList<>())));
        verify(keyHandoverRepository, never()).save(any(KeyHandover.class));
    }

    @Test
    void submitRejectsMissingRequiredFields() {
        KeyHandoverCreateRequest noFrom = validRequest(List.of(1L));
        noFrom.setHandoverFrom("  ");
        assertThrows(IllegalArgumentException.class, () -> service.submitHandover(noFrom));

        KeyHandoverCreateRequest noTo = validRequest(List.of(1L));
        noTo.setHandoverTo(null);
        assertThrows(IllegalArgumentException.class, () -> service.submitHandover(noTo));

        KeyHandoverCreateRequest noNote = validRequest(List.of(1L));
        noNote.setHandoverNote(" ");
        assertThrows(IllegalArgumentException.class, () -> service.submitHandover(noNote));

        verify(keyHandoverRepository, never()).save(any(KeyHandover.class));
        verify(keyHandoverItemRepository, never()).saveAll(any());
    }

    @Test
    void submitRejectsWhenNoOpenRecordAtSubmitTime() {
        // 窗口打开后所有借用已归还：提交时以台账实时状态为准，无需交班且不留痕迹
        when(keyBorrowRecordRepository.findByStatusOrderByCreateTimeDesc(KeyBorrowStatus.ON_LOAN))
                .thenReturn(Collections.emptyList());
        assertThrows(IllegalArgumentException.class,
                () -> service.submitHandover(validRequest(List.of(1L))));
        verify(keyHandoverRepository, never()).save(any(KeyHandover.class));
    }

    @Test
    void lockerHandoversReturnsEmptyForMissingLocker() {
        when(lockerRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.getLockerHandovers(99L));
    }
}
