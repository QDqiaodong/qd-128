package com.example.locker.dto;

import com.example.locker.enums.RepairStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairTicketDTO {

    private Long id;
    private String ticketNo;
    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;
    private String floor;
    private String lockerStatus;
    private String lockerStatusName;
    /** 故障格口编号 */
    private String compartmentNo;
    /** 故障现象 */
    private String symptom;
    /** 报修人 */
    private String reporter;
    private RepairStatus status;
    private String statusName;
    /** 处理中 = 尚未完工，列表与详情统一据此标记 */
    private Boolean processing;
    /** 处理人（完工时填写） */
    private String handler;
    /** 处理结果（完工时填写） */
    private String repairResult;
    /** 完工时间 */
    private LocalDateTime fixedTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
