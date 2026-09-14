package com.example.locker.dto;

import com.example.locker.enums.CollectionSuspensionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionSuspensionRecordDTO {

    private Long id;
    private String recordNo;
    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String lockerStatus;
    private String lockerStatusName;
    /** 开始停收时间 */
    private LocalDateTime suspendStartTime;
    /** 预计恢复时间 */
    private LocalDateTime expectedResumeTime;
    /** 值班人 */
    private String dutyOfficer;
    private CollectionSuspensionStatus status;
    private String statusName;
    /** 停收中 = 告示未撕、尚未确认恢复，台账与柜体「停收中」标记统一据此推导 */
    private Boolean suspended;
    /** 是否已过预计恢复时间仍未确认恢复（仅停收中记录可能为 true） */
    private Boolean overdue;
    /** 持续分钟数：停收中为距开始停收的时长，已恢复为开始停收到确认恢复的时长 */
    private Long elapsedMinutes;
    /** 确认恢复人 */
    private String resumeOperator;
    /** 确认恢复时间 */
    private LocalDateTime resumeTime;
    /** 恢复说明 */
    private String resumeNote;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
