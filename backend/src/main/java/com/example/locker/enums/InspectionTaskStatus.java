package com.example.locker.enums;

/**
 * 巡检任务状态
 */
public enum InspectionTaskStatus {

    PENDING("待开始"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成");

    private final String displayName;

    InspectionTaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InspectionTaskStatus fromCode(String code) {
        for (InspectionTaskStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的巡检任务状态: " + code);
    }
}
