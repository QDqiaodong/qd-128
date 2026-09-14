package com.example.locker.enums;

/**
 * 夜间停收转投台账状态
 */
public enum CollectionSuspensionStatus {

    SUSPENDED("停收中"),
    RESUMED("已恢复");

    private final String displayName;

    CollectionSuspensionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CollectionSuspensionStatus fromCode(String code) {
        for (CollectionSuspensionStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的停收状态: " + code);
    }
}
