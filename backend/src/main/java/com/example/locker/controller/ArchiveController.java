package com.example.locker.controller;

import com.example.locker.dto.ArchiveRequest;
import com.example.locker.dto.FilterRequest;
import com.example.locker.dto.LockerDTO;
import com.example.locker.entity.Archive;
import com.example.locker.service.ArchiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/archives")
@CrossOrigin(origins = "*")
public class ArchiveController {

    @Autowired
    private ArchiveService archiveService;

    @PostMapping
    public ResponseEntity<Archive> createArchive(@RequestBody Map<String, Object> request) {
        String archiveName = (String) request.get("archiveName");
        String filterConditions = (String) request.get("filterConditions");
        Integer resultCount = (Integer) request.get("resultCount");
        String operator = (String) request.get("operator");
        @SuppressWarnings("unchecked")
        List<Long> lockerIds = (List<Long>) request.get("lockerIds");

        ArchiveRequest archiveRequest = new ArchiveRequest();
        archiveRequest.setArchiveName(archiveName);
        archiveRequest.setFilterConditions(filterConditions);
        archiveRequest.setResultCount(resultCount);
        archiveRequest.setOperator(operator);

        return ResponseEntity.ok(archiveService.createArchive(archiveRequest, lockerIds));
    }

    @PostMapping("/from-filter")
    public ResponseEntity<Archive> createArchiveFromFilter(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> filterMap = (Map<String, Object>) request.get("filterRequest");
        String archiveName = (String) request.get("archiveName");
        String operator = (String) request.get("operator");

        FilterRequest filterRequest = new FilterRequest();
        if (filterMap.containsKey("buildingIds")) {
            @SuppressWarnings("unchecked")
            List<Long> buildingIds = ((List<Number>) filterMap.get("buildingIds")).stream()
                    .map(Number::longValue)
                    .toList();
            filterRequest.setBuildingIds(buildingIds);
        }
        if (filterMap.containsKey("unitIds")) {
            @SuppressWarnings("unchecked")
            List<Long> unitIds = ((List<Number>) filterMap.get("unitIds")).stream()
                    .map(Number::longValue)
                    .toList();
            filterRequest.setUnitIds(unitIds);
        }
        if (filterMap.containsKey("specTypes")) {
            @SuppressWarnings("unchecked")
            List<String> specTypes = (List<String>) filterMap.get("specTypes");
            filterRequest.setSpecTypes(specTypes);
        }
        if (filterMap.containsKey("startDate")) {
            filterRequest.setStartDate((String) filterMap.get("startDate"));
        }
        if (filterMap.containsKey("endDate")) {
            filterRequest.setEndDate((String) filterMap.get("endDate"));
        }

        return ResponseEntity.ok(archiveService.createArchiveFromFilter(filterRequest, archiveName, operator));
    }

    @GetMapping
    public ResponseEntity<Page<Archive>> getArchives(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(archiveService.getArchives(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Archive> getArchiveById(@PathVariable Long id) {
        return ResponseEntity.ok(archiveService.getArchiveById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArchive(@PathVariable Long id) {
        archiveService.deleteArchive(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/lockers")
    public ResponseEntity<List<LockerDTO>> getArchiveLockers(@PathVariable Long id) {
        return ResponseEntity.ok(archiveService.getArchiveLockers(id));
    }
}
