package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按柜滞留一览：正常柜全量列出，漏登滞留登记的柜体显示为「未登记」
 */
@Data
public class LockerClearanceOverviewDTO {

    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    /** 是否有办理中的清柜单（滞留中） */
    private Boolean overdue;
    /** 办理中的清柜单数 */
    private Integer openOrderCount;
    /** 办理中清柜单的滞留件数合计 */
    private Integer openPackageCount;
    /** 历史清柜单总数 */
    private Integer totalOrderCount;
    /** 最近一次发现时间 */
    private LocalDateTime lastFoundTime;
}
