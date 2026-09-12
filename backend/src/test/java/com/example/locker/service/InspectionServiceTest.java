package com.example.locker.service;

import com.example.locker.dto.InspectionScopeDTO;
import com.example.locker.dto.InspectionTaskCreateRequest;
import com.example.locker.dto.InspectionTaskDTO;
import com.example.locker.entity.Building;
import com.example.locker.entity.InspectionRecord;
import com.example.locker.entity.InspectionTask;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.InspectionIssueRepository;
import com.example.locker.repository.InspectionRecordRepository;
import com.example.locker.repository.InspectionTaskRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock
    private InspectionTaskRepository taskRepository;

    @Mock
    private InspectionRecordRepository recordRepository;

    @Mock
    private InspectionIssueRepository issueRepository;

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private SpecTemplateService specTemplateService;

    @InjectMocks
    private InspectionService inspectionService;

    private Building building;
    private Unit unit;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId(1L);
        building.setName("1号楼");

        unit = new Unit();
        unit.setId(2L);
        unit.setBuildingId(1L);
        unit.setName("1单元");
    }

    @Test
    void createTaskOnlyIncludesActiveLockersInBuilding() {
        Locker active = locker(11L, 1L, 2L, "KDG-ACTIVE", LockerStatus.ACTIVE);
        Locker temporarilyDisabled = locker(12L, 1L, 2L, "KDG-TEMP", LockerStatus.TEMPORARILY_DISABLED);
        Locker permanentlyDisabled = locker(13L, 1L, 3L, "KDG-PERM", LockerStatus.PERMANENTLY_DISABLED);

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(lockerRepository.findByBuildingIdAndStatus(1L, LockerStatus.ACTIVE))
                .thenReturn(List.of(active, temporarilyDisabled, permanentlyDisabled));
        when(taskRepository.save(any(InspectionTask.class))).thenAnswer(invocation -> {
            InspectionTask task = invocation.getArgument(0);
            task.setId(100L);
            return task;
        });
        when(issueRepository.findByTaskIdOrderByCreateTimeDesc(100L)).thenReturn(List.of());

        InspectionTaskDTO result = inspectionService.createTask(request(null, null));

        assertEquals(1, result.getTotalLockers());
        ArgumentCaptor<InspectionTask> taskCaptor = ArgumentCaptor.forClass(InspectionTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertEquals(1, taskCaptor.getValue().getTotalLockers());

        ArgumentCaptor<InspectionRecord> recordCaptor = ArgumentCaptor.forClass(InspectionRecord.class);
        verify(recordRepository, org.mockito.Mockito.times(1)).save(recordCaptor.capture());
        assertEquals(List.of(11L),
                recordCaptor.getAllValues().stream().map(InspectionRecord::getLockerId).toList());
    }

    @Test
    void createTaskFiltersManuallySuppliedDisabledLockerIds() {
        Locker active = locker(11L, 1L, 2L, "KDG-ACTIVE", LockerStatus.ACTIVE);

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(unitRepository.findById(2L)).thenReturn(Optional.of(unit));
        when(lockerRepository.findByUnitIdAndStatus(2L, LockerStatus.ACTIVE))
                .thenReturn(List.of(active));
        when(taskRepository.save(any(InspectionTask.class))).thenAnswer(invocation -> {
            InspectionTask task = invocation.getArgument(0);
            task.setId(101L);
            return task;
        });
        when(issueRepository.findByTaskIdOrderByCreateTimeDesc(101L)).thenReturn(List.of());

        InspectionTaskCreateRequest request = request(2L, List.of(11L, 12L, 13L, 999L));
        InspectionTaskDTO result = inspectionService.createTask(request);

        assertEquals(1, result.getTotalLockers());
        ArgumentCaptor<InspectionRecord> recordCaptor = ArgumentCaptor.forClass(InspectionRecord.class);
        verify(recordRepository).save(recordCaptor.capture());
        assertEquals(11L, recordCaptor.getValue().getLockerId());
    }

    @Test
    void createTaskRejectsScopeWithoutActiveLockers() {
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(lockerRepository.findByBuildingIdAndStatus(1L, LockerStatus.ACTIVE)).thenReturn(List.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> inspectionService.createTask(request(null, null)));
        assertEquals("所选楼栋/单元下暂无可巡检的正常快递柜，无法发起巡检任务", exception.getMessage());
        verify(taskRepository, never()).save(any());
        verify(recordRepository, never()).save(any());
    }

    @Test
    void scopeExposesOnlyActiveLockers() {
        Locker active = locker(11L, 1L, 2L, "KDG-ACTIVE", LockerStatus.ACTIVE);
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(unitRepository.findById(2L)).thenReturn(Optional.of(unit));
        when(lockerRepository.findByUnitIdAndStatus(2L, LockerStatus.ACTIVE)).thenReturn(List.of(active));
        when(specTemplateService.getSpecTypeName("STANDARD")).thenReturn("标准型");

        InspectionScopeDTO scope = inspectionService.getScope(1L, 2L);

        assertEquals(1, scope.getTotalLockers());
        assertEquals(1, scope.getLockers().size());
        assertEquals(11L, scope.getLockers().get(0).getId());
        verify(lockerRepository).findByUnitIdAndStatus(eq(2L), eq(LockerStatus.ACTIVE));
    }

    private InspectionTaskCreateRequest request(Long unitId, List<Long> lockerIds) {
        InspectionTaskCreateRequest request = new InspectionTaskCreateRequest();
        request.setTaskName("巡检任务");
        request.setBuildingId(1L);
        request.setUnitId(unitId);
        request.setCycle("ONCE");
        request.setAssignee("张三");
        request.setLockerIds(lockerIds);
        return request;
    }

    private Locker locker(Long id, Long buildingId, Long unitId, String lockerNo, LockerStatus status) {
        Locker locker = new Locker();
        locker.setId(id);
        locker.setBuildingId(buildingId);
        locker.setUnitId(unitId);
        locker.setLockerNo(lockerNo);
        locker.setSpecType("STANDARD");
        locker.setCompartmentCount(24);
        locker.setStatus(status);
        return locker;
    }
}
