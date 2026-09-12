package com.example.locker.dto;

import lombok.Data;

/**
 * 办结清柜单请求：处理结果必填
 */
@Data
public class ClearanceCompleteRequest {

    private String handleResult;
}
