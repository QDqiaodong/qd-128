package com.example.locker.dto;

import lombok.Data;

import java.util.List;

/**
 * 巡检填报提交：支持逐台（单台）或一次性多台提交。
 */
@Data
public class InspectionSubmitRequest {

    private List<RecordSubmitItem> items;
}
