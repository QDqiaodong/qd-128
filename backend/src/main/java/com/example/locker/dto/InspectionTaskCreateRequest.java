package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InspectionTaskCreateRequest {

    private String taskName;

    private Long buildingId;

    /** 单元 ID，可为空表示整栋楼 */
    private Long unitId;

    /** 巡检周期 code：ONCE/DAILY/WEEKLY/MONTHLY */
    private String cycle;

    private String assignee;

    private LocalDateTime deadline;

    private String creator;
}
