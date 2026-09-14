package com.example.locker.dto;

import lombok.Data;

/**
 * 确认柜门已关闭的请求。
 */
@Data
public class DoorAlarmCloseRequest {

    /** 确认关闭人（选填，默认系统管理员） */
    private String closeOperator;
    /** 关闭说明（选填） */
    private String closeNote;
}
