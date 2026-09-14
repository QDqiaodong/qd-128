package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoorAlarmCreateRequest {

    private Long lockerId;
    /** 发现柜门未关时间（选填，默认当前时间；补登过去时间不能晚于当前时间） */
    private LocalDateTime doorOpenTime;
    /** 上报人/发现人（必填） */
    private String reporter;
    /** 约定关严分钟数（选填，默认系统约定值） */
    private Integer thresholdMinutes;
    private String remark;
}
