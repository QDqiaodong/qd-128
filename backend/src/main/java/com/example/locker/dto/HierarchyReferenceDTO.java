package com.example.locker.dto;

import lombok.Data;

/**
 * 物业层级删除前的关联影响统计。
 * lockerCount 为关联快递柜数量，archiveCount 为引用这些快递柜的归档快照数量（去重）。
 */
@Data
public class HierarchyReferenceDTO {

    private Long id;

    /** 层级类型：BUILDING-楼栋，UNIT-单元 */
    private String type;

    /** 关联快递柜数量 */
    private long lockerCount;

    /** 关联归档快照数量（去重） */
    private long archiveCount;

    /** 楼栋下的单元数量（仅楼栋使用） */
    private long unitCount;

    /** 是否允许删除（无快递柜及归档快照关联时为 true） */
    private boolean deletable;

    public static HierarchyReferenceDTO of(Long id, String type, long lockerCount, long archiveCount, long unitCount) {
        HierarchyReferenceDTO dto = new HierarchyReferenceDTO();
        dto.setId(id);
        dto.setType(type);
        dto.setLockerCount(lockerCount);
        dto.setArchiveCount(archiveCount);
        dto.setUnitCount(unitCount);
        dto.setDeletable(lockerCount == 0 && archiveCount == 0);
        return dto;
    }
}
