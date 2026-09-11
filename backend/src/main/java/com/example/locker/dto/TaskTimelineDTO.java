package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务操作时间线：合并展示转派与催办记录，按时间倒序。
 */
@Data
public class TaskTimelineDTO {

    /** 条目类型：REASSIGN-转派，URGE-催办 */
    private String type;

    private String typeName;

    private Long id;

    private LocalDateTime operateTime;

    private String operator;

    /** 转派：原负责人 */
    private String oldAssignee;

    /** 转派：新负责人 */
    private String newAssignee;

    /** 转派原因 / 催办说明 */
    private String content;
}
