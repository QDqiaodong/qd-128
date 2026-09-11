package com.example.locker.enums;

/**
 * 巡检检查项（每台快递柜逐台检查）
 */
public enum InspectionCheckItem {

    COMPARTMENT("compartment", "格口"),
    SCREEN("screen", "屏幕"),
    LOCK("lock", "门锁");

    private final String code;
    private final String displayName;

    InspectionCheckItem(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InspectionCheckItem fromCode(String code) {
        for (InspectionCheckItem item : values()) {
            if (item.code.equals(code) || item.name().equals(code)) {
                return item;
            }
        }
        throw new IllegalArgumentException("无效的检查项: " + code);
    }
}
