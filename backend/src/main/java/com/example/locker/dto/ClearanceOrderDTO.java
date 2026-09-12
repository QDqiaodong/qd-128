package com.example.locker.dto;

import com.example.locker.enums.ClearanceStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ClearanceOrderDTO {

    private Long id;
    private String orderNo;
    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    /** 柜体当前生命周期状态（永久停用柜的历史滞留单仍可查看） */
    private String lockerStatus;
    private String lockerStatusName;
    private String overdueCompartments;
    private Integer packageCount;
    private LocalDateTime foundTime;
    private String handler;
    private ClearanceStatus status;
    private String statusName;
    /** 办理中的清柜单置为 true，列表与详情据此标记「滞留中」 */
    private Boolean overdue;
    private String handleResult;
    private LocalDateTime completeTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 累计催领次数（由催领台账实时统计） */
    private Integer urgeCount;

    /** 是否存在未关闭催领（办理中的单同一时刻最多一笔） */
    private Boolean openUrge;

    /** 当面催领台账（按催领时间倒序） */
    private List<ClearanceUrgeRecordDTO> urgeRecords;
}
