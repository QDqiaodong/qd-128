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
    /** 是否有借用中的钥匙借用记录（借用中标记，由借用台账实时推导，刷新后保持一致） */
    private Boolean keyBorrowed;
    /** 未还借用条数（借用中记录数） */
    private Integer openKeyBorrowCount;
    /** 本月是否已抄电表（已抄/未抄标记，由有效抄表单实时推导，刷新后保持一致） */
    private Boolean meterReadThisMonth;
    /** 是否有处理中的格口报修单（维修中标记，由报修台账实时推导，刷新后保持一致） */
    private Boolean repairing;
    /** 处理中报修条数 */
    private Integer openRepairCount;
    /** 是否有未处理的柜门未关告警（柜门未关标记，由告警台账实时推导，刷新后保持一致） */
    private Boolean doorAjar;
    /** 未处理柜门未关告警条数 */
    private Integer openDoorAlarmCount;
    /** 是否有停收中的夜间停收转投记录（停收中标记，由停收台账实时推导，刷新后保持一致） */
    private Boolean collectionSuspended;
    /** 停收中记录条数 */
    private Integer openCollectionSuspensionCount;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
