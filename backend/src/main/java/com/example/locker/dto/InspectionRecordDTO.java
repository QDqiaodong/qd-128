package com.example.locker.dto;

import com.example.locker.enums.CheckResult;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检任务中单台快递柜的检查明细。
 */
@Data
public class InspectionRecordDTO {

    private Long id;
    private Long taskId;
    private Long lockerId;
    private String lockerNo;
    private String specTypeName;
    private Integer compartmentCount;
    private String buildingName;
    private String unitName;
    private String floor;
    /** 柜体是否还存在（被删除时仅保留编号占位） */
    private Boolean lockerExists;

    private CheckResult compartmentResult;
    private CheckResult screenResult;
    private CheckResult lockResult;
    private String remark;
    private String inspector;
    private LocalDateTime inspectTime;

    /**
     * 柜级巡检状态（服务端权威口径）：任一检查项已填报为 INSPECTED（已巡），否则 UNINSPECTED（未巡）。
     * 列表筛选与详情标记统一取该字段，避免两边口径不一致。
     */
    private String inspectionStatus;

    /** 该柜体当前待处理异常数 */
    private Integer pendingIssueCount;
    /** 该柜体异常记录总数（含已解决） */
    private Integer totalIssueCount;

    public boolean isFilled() {
        return compartmentResult != null || screenResult != null || lockResult != null;
    }
}
