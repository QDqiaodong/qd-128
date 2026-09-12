package com.example.locker.enums;

/**
 * 滞留件清柜单状态
 */
public enum ClearanceStatus {

    PROCESSING("办理中"),
    COMPLETED("已办结");

    private final String displayName;

    ClearanceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ClearanceStatus fromCode(String code) {
        for (ClearanceStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的清柜单状态: " + code);
    }
}
