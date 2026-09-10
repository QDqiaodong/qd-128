package com.example.locker.enums;

/**
 * 快递柜生命周期状态
 */
public enum LockerStatus {

    ACTIVE("正常"),
    TEMPORARILY_DISABLED("临时停用"),
    PERMANENTLY_DISABLED("永久停用");

    private final String displayName;

    LockerStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LockerStatus fromCode(String code) {
        for (LockerStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的柜体状态: " + code);
    }
}
