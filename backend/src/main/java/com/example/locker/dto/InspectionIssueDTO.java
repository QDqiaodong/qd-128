package com.example.locker.dto;

import com.example.locker.enums.IssueStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InspectionIssueDTO {

    private Long id;
    private Long taskId;
    private Long recordId;
    private Long lockerId;
    private String lockerNo;
    private String buildingName;
    private String unitName;

    private String checkItem;
    private String checkItemName;
    private String description;
    private IssueStatus status;
    private String statusName;
    private String handler;
    private String handleNote;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
