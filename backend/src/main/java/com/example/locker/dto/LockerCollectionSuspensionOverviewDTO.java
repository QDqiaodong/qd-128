package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LockerCollectionSuspensionOverviewDTO {

    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String status;
    private String statusName;
    /** 是否存在停收中的停收记录（存在则柜体标记「停收中」） */
    private Boolean suspended;
    /** 停收中记录条数 */
    private Integer openRecordCount;
    /** 停收中记录是否有已过预计恢复时间的 */
    private Boolean overdue;
    /** 历史记录总条数（含已恢复） */
    private Integer totalRecordCount;
    /** 最近一次开始停收时间 */
    private LocalDateTime lastSuspendStartTime;
    /** 当前停收记录的预计恢复时间（停收中时返回） */
    private LocalDateTime expectedResumeTime;
}
