package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class LockerDTO {

    private Long id;
    private String lockerNo;
    private Integer compartmentCount;
    private String specType;
    private String specTypeName;
    private Long buildingId;
    private String buildingName;
    private Long unitId;
    private String unitName;
    private String floor;
    private LocalDate installationDate;
    /** 柜体当前状态 */
    private String status;
    private String statusName;
    /** 归档场景下该柜体在快照时刻的状态（可能与当前状态不同） */
    private String snapshotStatus;
    private String snapshotStatusName;
    private Boolean fromSnapshot;
    /** 归档场景下冗余的当前状态，便于与快照对比 */
    private String currentStatus;
    private String currentStatusName;
    /** 是否有办理中的滞留清柜单（滞留中标记，由清柜单实时推导，刷新后保持一致） */
    private Boolean overdue;
    /** 办理中的清柜单数 */
    private Integer openClearanceCount;
    /** 办理中清柜单的滞留件数合计 */
    private Integer overduePackageCount;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
