package com.example.locker.enums;

/**
 * 钥匙借用台账状态
 */
public enum KeyBorrowStatus {

    ON_LOAN("借用中"),
    RETURNED("已归还");

    private final String displayName;

    KeyBorrowStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static KeyBorrowStatus fromCode(String code) {
        for (KeyBorrowStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的钥匙借用状态: " + code);
    }
}
