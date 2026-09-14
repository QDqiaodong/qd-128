package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionSuspensionCreateRequest {

    private Long lockerId;
    /** 开始停收时间（选填，默认当前时间；补登过去时间不能晚于当前时间） */
    private LocalDateTime suspendStartTime;
    /** 预计恢复时间（必填，必须晚于开始停收时间） */
    private LocalDateTime expectedResumeTime;
    /** 值班人（必填） */
    private String dutyOfficer;
    private String remark;
}
