package com.example.locker.enums;

/**
 * 电表抄表单状态
 */
public enum MeterReadingStatus {

    ACTIVE("有效"),
    VOIDED("已作废");

    private final String displayName;

    MeterReadingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MeterReadingStatus fromCode(String code) {
        for (MeterReadingStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的抄表单状态: " + code);
    }
}
