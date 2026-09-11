package com.example.locker.enums;

/**
 * 巡检周期
 */
public enum InspectionCycle {

    ONCE("一次性"),
    DAILY("每日"),
    WEEKLY("每周"),
    MONTHLY("每月");

    private final String displayName;

    InspectionCycle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InspectionCycle fromCode(String code) {
        for (InspectionCycle cycle : values()) {
            if (cycle.name().equals(code)) {
                return cycle;
            }
        }
        throw new IllegalArgumentException("无效的巡检周期: " + code);
    }
}
