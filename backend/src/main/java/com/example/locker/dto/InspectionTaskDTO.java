package com.example.locker.dto;

import com.example.locker.enums.InspectionCycle;
import com.example.locker.enums.InspectionTaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InspectionTaskDTO {

    private Long id;
    private String taskName;
    private Long buildingId;
    private String buildingName;
    private Long unitId;
    private String unitName;
    private InspectionCycle cycle;
    private String cycleName;
    private String assignee;
    private LocalDateTime deadline;
    private InspectionTaskStatus status;
    private String statusName;

    private Integer totalLockers;
    private Integer completedLockers;
    /** 发生异常的柜体数量 */
    private Integer abnormalCount;
    /** 待处理（含处理中）的异常记录数量 */
    private Integer pendingIssueCount;
    /** 异常记录总数（含已解决） */
    private Integer totalIssueCount;

    /** 催办次数（由催办台账实时统计） */
    private Integer urgeCount;

    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 进度百分比 0-100，由后端按完成柜体数计算，保证刷新后一致 */
    private Integer progress;

    /** 是否已逾期（截止时间已过且任务未完成） */
    private Boolean overdue;
}
