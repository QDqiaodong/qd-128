package com.example.locker.enums;

/**
 * 单个检查项的检查结果
 */
public enum CheckResult {

    NORMAL("正常"),
    ABNORMAL("异常"),
    NOT_APPLICABLE("不适用");

    private final String displayName;

    CheckResult(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CheckResult fromCode(String code) {
        for (CheckResult result : values()) {
            if (result.name().equals(code)) {
                return result;
            }
        }
        throw new IllegalArgumentException("无效的检查结果: " + code);
    }
}
