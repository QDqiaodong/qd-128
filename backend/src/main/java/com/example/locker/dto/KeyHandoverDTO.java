package com.example.locker.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 钥匙交接班记录：台账中留存的交接痕迹 */
@Data
public class KeyHandoverDTO {

    private Long id;

    private String handoverNo;

    private String handoverFrom;

    private String handoverTo;

    private String handoverNote;

    /** 本次点名的未还柜数量 */
    private Integer itemCount;

    private LocalDateTime createTime;

    private List<KeyHandoverItemDTO> items;
}
