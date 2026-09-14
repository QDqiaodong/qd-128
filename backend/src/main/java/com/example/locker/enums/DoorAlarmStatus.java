package com.example.locker.enums;

/**
 * 柜门未关告警台账状态
 */
public enum DoorAlarmStatus {

    OPEN("未处理"),
    CLOSED("已关闭");

    private final String displayName;

    DoorAlarmStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DoorAlarmStatus fromCode(String code) {
        for (DoorAlarmStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的告警状态: " + code);
    }
}
