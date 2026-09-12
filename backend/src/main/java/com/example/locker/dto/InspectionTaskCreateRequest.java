package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InspectionTaskCreateRequest {

    private String taskName;

    private Long buildingId;

    /** 单元 ID，可为空表示整栋楼 */
    private Long unitId;

    /**
     * 可选柜体 ID。后端会再次按楼栋/单元范围和 ACTIVE 状态过滤，
     * 即使前端被篡改传入停用柜，也不会生成对应待检明细。
     */
    private List<Long> lockerIds;

    /** 巡检周期 code：ONCE/DAILY/WEEKLY/MONTHLY */
    private String cycle;

    private String assignee;

    private LocalDateTime deadline;

    private String creator;
}
