package com.example.locker.entity;

import com.example.locker.enums.DoorAlarmStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 柜门未关告警台账：巡柜发现取件后柜门虚掩/未关严时登记（未处理），
 * 超过约定分钟仍未关严的柜必须能在本台账中查到；
 * 确认柜门已关严后点「确认已关闭」，同一条记录状态变为已关闭，
 * 柜体列表/详情的「柜门未关」标记以本表为唯一数据源实时推导，随之恢复正常。
 */
@Entity
@Table(name = "door_alarm_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoorAlarmRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alarm_no", unique = true, nullable = false, length = 40)
    private String alarmNo;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /** 发现柜门未关时间（默认当前时间，可补登过去时间） */
    @Column(name = "door_open_time", nullable = false)
    private LocalDateTime doorOpenTime;

    /** 约定关严分钟数：超过该时长仍未确认关严即为超时告警 */
    @Column(name = "threshold_minutes", nullable = false)
    private Integer thresholdMinutes;

    /** 上报人（巡柜发现人） */
    @Column(name = "reporter", nullable = false, length = 50)
    private String reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DoorAlarmStatus status = DoorAlarmStatus.OPEN;

    /** 确认关闭人（留空默认系统管理员） */
    @Column(name = "close_operator", length = 50)
    private String closeOperator;

    /** 确认关闭时间 */
    @Column(name = "close_time")
    private LocalDateTime closeTime;

    /** 关闭说明 */
    @Column(name = "close_note", length = 500)
    private String closeNote;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
