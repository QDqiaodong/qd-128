package com.example.locker.dto;

import lombok.Data;

import java.util.List;

/**
 * 钥匙交接班提交请求：交班人勾选当前全部借用中的柜（recordIds）逐一点名，
 * 并填写接班人和交接说明。recordIds 必须与提交时刻全部未还记录一一对应，不能勾漏或勾选已还/不存在的单。
 */
@Data
public class KeyHandoverCreateRequest {

    /** 交班人（必填） */
    private String handoverFrom;

    /** 接班人（必填） */
    private String handoverTo;

    /** 交接说明（必填） */
    private String handoverNote;

    /** 点名勾选的未还借用记录 ID，必须勾齐全部未还柜 */
    private List<Long> recordIds;
}
