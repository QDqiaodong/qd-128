package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按柜报修状态一览行：处理中条数与可用标记由报修台账实时推导。
 */
@Data
public class LockerRepairOverviewDTO {

    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String status;
    private String statusName;
    /** 是否存在处理中的报修单（存在则柜体标记维修中，否则可用） */
    private Boolean repairing;
    /** 处理中报修条数 */
    private Integer openTicketCount;
    /** 历史报修总条数（含已修好） */
    private Integer totalTicketCount;
    /** 最近一次报修时间 */
    private LocalDateTime lastReportTime;
}
