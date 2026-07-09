SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS building (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '楼栋名称',
    code VARCHAR(50) UNIQUE COMMENT '楼栋编码',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼栋表';

CREATE TABLE IF NOT EXISTS unit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id BIGINT NOT NULL COMMENT '楼栋ID',
    name VARCHAR(100) NOT NULL COMMENT '单元名称',
    code VARCHAR(50) COMMENT '单元编码',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (building_id) REFERENCES building(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元表';

CREATE TABLE IF NOT EXISTS locker (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    locker_no VARCHAR(50) UNIQUE NOT NULL COMMENT '柜体编号',
    compartment_count INT NOT NULL COMMENT '格口数量',
    spec_type VARCHAR(20) NOT NULL COMMENT '柜体规格',
    building_id BIGINT NOT NULL COMMENT '所属楼栋',
    unit_id BIGINT NOT NULL COMMENT '所属单元',
    floor VARCHAR(20) COMMENT '楼层',
    installation_date DATE COMMENT '安装日期',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (building_id) REFERENCES building(id),
    FOREIGN KEY (unit_id) REFERENCES unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递柜表';

CREATE TABLE IF NOT EXISTS adjustment_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    old_building_id BIGINT COMMENT '原楼栋ID',
    old_unit_id BIGINT COMMENT '原单元ID',
    new_building_id BIGINT NOT NULL COMMENT '新楼栋ID',
    new_unit_id BIGINT NOT NULL COMMENT '新单元ID',
    reason TEXT COMMENT '调整原因',
    operator VARCHAR(50) COMMENT '操作人',
    adjust_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
    FOREIGN KEY (locker_id) REFERENCES locker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归属调整记录表';

CREATE TABLE IF NOT EXISTS archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_name VARCHAR(200) NOT NULL COMMENT '归档名称',
    filter_conditions TEXT COMMENT '筛选条件JSON',
    result_count INT NOT NULL COMMENT '结果数量',
    operator VARCHAR(50) COMMENT '操作人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='筛选快照归档表';

CREATE TABLE IF NOT EXISTS archive_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_id BIGINT NOT NULL COMMENT '归档ID',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (archive_id) REFERENCES archive(id) ON DELETE CASCADE,
    FOREIGN KEY (locker_id) REFERENCES locker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归档明细表';

INSERT INTO building (name, code, sort_order) VALUES 
('1号楼', 'B001', 1),
('2号楼', 'B002', 2),
('3号楼', 'B003', 3);

INSERT INTO unit (building_id, name, code, sort_order) VALUES 
(1, '1单元', 'U001', 1),
(1, '2单元', 'U002', 2),
(2, '1单元', 'U003', 1),
(2, '2单元', 'U004', 2),
(3, '1单元', 'U005', 1);
