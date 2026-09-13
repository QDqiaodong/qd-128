package com.example.locker.enums;

/**
 * 格口报修台账状态
 */
public enum RepairStatus {

    PROCESSING("处理中"),
    FIXED("已修好");

    private final String displayName;

    RepairStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RepairStatus fromCode(String code) {
        for (RepairStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的报修状态: " + code);
    }
}
