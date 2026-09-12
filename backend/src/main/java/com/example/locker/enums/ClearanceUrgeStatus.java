package com.example.locker.enums;

/**
 * 清柜单当面催领记录状态
 */
public enum ClearanceUrgeStatus {

    OPEN("未关闭"),
    CLOSED("已关闭");

    private final String displayName;

    ClearanceUrgeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
