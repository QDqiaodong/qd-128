package com.example.locker.enums;

/**
 * 异常待处理记录状态
 */
public enum IssueStatus {

    PENDING("待处理"),
    PROCESSING("处理中"),
    RESOLVED("已解决");

    private final String displayName;

    IssueStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static IssueStatus fromCode(String code) {
        for (IssueStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的异常处理状态: " + code);
    }
}
