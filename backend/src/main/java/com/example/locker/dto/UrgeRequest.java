package com.example.locker.dto;

import lombok.Data;

@Data
public class UrgeRequest {

    /** 催办说明（必填） */
    private String note;

    /** 催办人（选填） */
    private String operator;
}
