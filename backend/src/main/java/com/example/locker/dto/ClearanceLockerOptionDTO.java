package com.example.locker.dto;

import lombok.Data;

/**
 * 清柜登记可选柜体：包含全部生命周期状态（永久停用柜可补登历史滞留）
 */
@Data
public class ClearanceLockerOptionDTO {

    private Long id;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String status;
    private String statusName;
    /** 当前是否有办理中的清柜单 */
    private Boolean overdue;
}
