package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LockerDoorAlarmOverviewDTO {

    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String status;
    private String statusName;
    /** 是否存在未处理的柜门未关告警（存在则柜体标记「柜门未关」） */
    private Boolean doorAjar;
    /** 未处理告警条数 */
    private Integer openAlarmCount;
    /** 未处理告警中是否有已超约定分钟的 */
    private Boolean overtime;
    /** 历史告警总条数（含已关闭） */
    private Integer totalAlarmCount;
    /** 最近一次发现未关时间 */
    private LocalDateTime lastDoorOpenTime;
}
