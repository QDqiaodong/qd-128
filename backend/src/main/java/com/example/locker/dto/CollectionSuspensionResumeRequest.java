package com.example.locker.dto;

import lombok.Data;

/**
 * 确认已恢复的请求。
 */
@Data
public class CollectionSuspensionResumeRequest {

    /** 确认恢复人（选填，默认系统管理员） */
    private String resumeOperator;
    /** 恢复说明（选填） */
    private String resumeNote;
}
