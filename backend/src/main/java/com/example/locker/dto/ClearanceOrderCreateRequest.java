package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登记滞留件清柜单请求
 */
@Data
public class ClearanceOrderCreateRequest {

    private Long lockerId;
    /** 滞留格口，如 A03,A07 */
    private String overdueCompartments;
    /** 滞留件数 */
    private Integer packageCount;
    /** 发现时间（历史补登可为过去时间） */
    private LocalDateTime foundTime;
    /** 处理人 */
    private String handler;
    private String remark;
}
