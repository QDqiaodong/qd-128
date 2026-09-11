package com.example.locker.dto;

import lombok.Data;

/**
 * 提交某台快递柜的逐项检查结果。
 */
@Data
public class RecordSubmitItem {

    private Long lockerId;

    /** compartment/screen/lock 各检查项结果：NORMAL/ABNORMAL/NOT_APPLICABLE */
    private String compartmentResult;
    private String screenResult;
    private String lockResult;

    private String remark;
    private String inspector;
}
