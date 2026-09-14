package com.example.locker.dto;

import com.example.locker.enums.DoorAlarmStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoorAlarmRecordDTO {

    private Long id;
    private String alarmNo;
    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String lockerStatus;
    private String lockerStatusName;
    /** 发现柜门未关时间 */
    private LocalDateTime doorOpenTime;
    /** 约定关严分钟数 */
    private Integer thresholdMinutes;
    /** 上报人 */
    private String reporter;
    private DoorAlarmStatus status;
    private String statusName;
    /** 未处理 = 柜门仍未确认关严，台账与柜体「柜门未关」标记统一据此推导 */
    private Boolean open;
    /** 是否已超约定分钟仍未关严（仅未处理记录可能为 true） */
    private Boolean overtime;
    /** 持续分钟数：未处理为距发现未关的时长，已关闭为发现到确认关闭的时长 */
    private Long elapsedMinutes;
    /** 确认关闭人 */
    private String closeOperator;
    /** 确认关闭时间 */
    private LocalDateTime closeTime;
    /** 关闭说明 */
    private String closeNote;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
