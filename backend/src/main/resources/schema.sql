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
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '生命周期状态: ACTIVE-正常, TEMPORARILY_DISABLED-临时停用, PERMANENTLY_DISABLED-永久停用',
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

CREATE TABLE IF NOT EXISTS status_change_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    old_status VARCHAR(20) NOT NULL COMMENT '变更前状态',
    new_status VARCHAR(20) NOT NULL COMMENT '变更后状态',
    reason TEXT COMMENT '变更原因',
    operator VARCHAR(50) COMMENT '操作人',
    change_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    INDEX idx_locker_id (locker_id),
    FOREIGN KEY (locker_id) REFERENCES locker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='柜体生命周期状态变更记录表';

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
    status_snapshot VARCHAR(20) COMMENT '归档时柜体生命周期状态快照',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (archive_id) REFERENCES archive(id) ON DELETE CASCADE,
    FOREIGN KEY (locker_id) REFERENCES locker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归档明细表';

CREATE TABLE IF NOT EXISTS inspection_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(200) NOT NULL COMMENT '巡检任务名称',
    building_id BIGINT NOT NULL COMMENT '巡检楼栋ID',
    unit_id BIGINT COMMENT '巡检单元ID，为空表示整栋楼',
    cycle VARCHAR(20) NOT NULL DEFAULT 'ONCE' COMMENT '巡检周期: ONCE-一次性, DAILY-每日, WEEKLY-每周, MONTHLY-每月',
    assignee VARCHAR(50) COMMENT '负责人',
    deadline DATETIME COMMENT '截止时间',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING-待开始, IN_PROGRESS-进行中, COMPLETED-已完成',
    total_lockers INT NOT NULL DEFAULT 0 COMMENT '应检柜体总数',
    completed_lockers INT NOT NULL DEFAULT 0 COMMENT '已检柜体数',
    abnormal_count INT NOT NULL DEFAULT 0 COMMENT '异常柜体数',
    pending_issue_count INT NOT NULL DEFAULT 0 COMMENT '待处理异常记录数(含处理中)',
    creator VARCHAR(50) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_inspection_building (building_id),
    INDEX idx_inspection_unit (unit_id),
    INDEX idx_inspection_status (status)
    -- 仅按 ID 关联楼栋/单元而不加外键，避免与物业层级删除流程冲突；层级名称实时按 ID 解析
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递柜巡检任务表';

CREATE TABLE IF NOT EXISTS inspection_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '巡检任务ID',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    compartment_result VARCHAR(20) COMMENT '格口检查结果: NORMAL/ABNORMAL/NOT_APPLICABLE',
    screen_result VARCHAR(20) COMMENT '屏幕检查结果: NORMAL/ABNORMAL/NOT_APPLICABLE',
    lock_result VARCHAR(20) COMMENT '门锁检查结果: NORMAL/ABNORMAL/NOT_APPLICABLE',
    remark TEXT COMMENT '备注',
    inspector VARCHAR(50) COMMENT '巡检人',
    inspect_time DATETIME COMMENT '巡检时间',
    UNIQUE KEY uk_task_locker (task_id, locker_id),
    INDEX idx_record_task (task_id),
    FOREIGN KEY (task_id) REFERENCES inspection_task(id) ON DELETE CASCADE,
    FOREIGN KEY (locker_id) REFERENCES locker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检明细表（任务-柜体）';

CREATE TABLE IF NOT EXISTS inspection_issue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '巡检任务ID',
    record_id BIGINT NOT NULL COMMENT '巡检明细ID',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    check_item VARCHAR(20) NOT NULL COMMENT '异常检查项: compartment-格口, screen-屏幕, lock-门锁',
    description TEXT COMMENT '异常描述',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING-待处理, PROCESSING-处理中, RESOLVED-已解决',
    handler VARCHAR(50) COMMENT '处理人',
    handle_note TEXT COMMENT '处理说明',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    handle_time DATETIME COMMENT '处理时间',
    INDEX idx_issue_task (task_id),
    INDEX idx_issue_locker (locker_id),
    INDEX idx_issue_status (status),
    FOREIGN KEY (task_id) REFERENCES inspection_task(id) ON DELETE CASCADE,
    FOREIGN KEY (record_id) REFERENCES inspection_record(id) ON DELETE CASCADE,
    FOREIGN KEY (locker_id) REFERENCES locker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检异常待处理记录表';

CREATE TABLE IF NOT EXISTS clearance_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL UNIQUE COMMENT '清柜单号',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    overdue_compartments VARCHAR(500) NOT NULL COMMENT '滞留格口',
    package_count INT NOT NULL COMMENT '滞留件数',
    found_time DATETIME NOT NULL COMMENT '发现时间',
    handler VARCHAR(50) NOT NULL COMMENT '处理人',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '单据状态: PROCESSING-办理中, COMPLETED-已办结',
    handle_result TEXT COMMENT '处理结果(办结必填)',
    complete_time DATETIME COMMENT '办结时间',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_clearance_locker (locker_id),
    INDEX idx_clearance_status (status),
    FOREIGN KEY (locker_id) REFERENCES locker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='滞留件清柜单表';

CREATE TABLE IF NOT EXISTS key_borrow_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_no VARCHAR(40) NOT NULL UNIQUE COMMENT '借用台账编号',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    borrower VARCHAR(50) NOT NULL COMMENT '借出人',
    reason VARCHAR(500) NOT NULL COMMENT '借用事由',
    borrow_time DATETIME NOT NULL COMMENT '借出时间',
    expected_return_time DATETIME NOT NULL COMMENT '预计归还时间',
    status VARCHAR(20) NOT NULL DEFAULT 'ON_LOAN' COMMENT '借用状态: ON_LOAN-借用中, RETURNED-已归还',
    returner VARCHAR(50) COMMENT '归还人(归还必填)',
    return_time DATETIME COMMENT '归还时间(归还必填)',
    remark TEXT COMMENT '备注',
    extend_count INT NOT NULL DEFAULT 0 COMMENT '改期次数',
    last_extend_reason VARCHAR(500) COMMENT '最近一次改期原因',
    last_extend_time DATETIME COMMENT '最近一次改期时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_key_borrow_locker (locker_id),
    INDEX idx_key_borrow_status (status),
    FOREIGN KEY (locker_id) REFERENCES locker(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递柜柜门钥匙借用台账表';

CREATE TABLE IF NOT EXISTS meter_reading_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_no VARCHAR(40) NOT NULL UNIQUE COMMENT '抄表单号',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    period_month VARCHAR(7) NOT NULL COMMENT '账期(自然月, 格式yyyy-MM), 由抄表时间推导',
    reading_value DECIMAL(12,2) NOT NULL COMMENT '电表读数(kWh)',
    reader VARCHAR(50) NOT NULL COMMENT '抄表人',
    reading_time DATETIME NOT NULL COMMENT '抄表时间',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '单据状态: ACTIVE-有效, VOIDED-已作废',
    void_reason VARCHAR(500) COMMENT '作废原因(作废必填)',
    void_operator VARCHAR(50) COMMENT '作废人',
    void_time DATETIME COMMENT '作废时间',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_meter_reading_locker (locker_id),
    INDEX idx_meter_reading_period (period_month),
    INDEX idx_meter_reading_status (status),
    FOREIGN KEY (locker_id) REFERENCES locker(id)
    -- 同一柜同一自然月只允许一张未作废(有效)抄表单，由后端在登记事务内校验拦截
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递柜电表抄表单表';

-- ===================== 种子数据（幂等，可重复执行） =====================
-- 说明：docker-entrypoint-initdb.d 只在空数据卷首次初始化时执行本脚本；
-- 若数据卷中已有楼栋/单元（旧版本初始化、初始化中断后重启等），
-- 普通 INSERT 会因唯一键冲突中断整个脚本，导致后面的柜体种子永远插不进去。
-- 因此所有种子语句都做到可重复执行：已有记录跳过，缺失记录补种。

-- 楼栋：code 全局唯一，INSERT IGNORE 跳过已存在的编码
INSERT IGNORE INTO building (name, code, sort_order) VALUES
('1号楼', 'B001', 1),
('2号楼', 'B002', 2),
('3号楼', 'B003', 3);

-- 单元：按楼栋编码实时解析 building_id（不依赖固定自增 ID），同编码单元已存在则跳过
INSERT INTO unit (building_id, name, code, sort_order)
SELECT b.id, '1单元', 'U001', 1 FROM building b
WHERE b.code = 'B001' AND NOT EXISTS (SELECT 1 FROM unit u WHERE u.code = 'U001');

INSERT INTO unit (building_id, name, code, sort_order)
SELECT b.id, '2单元', 'U002', 2 FROM building b
WHERE b.code = 'B001' AND NOT EXISTS (SELECT 1 FROM unit u WHERE u.code = 'U002');

INSERT INTO unit (building_id, name, code, sort_order)
SELECT b.id, '1单元', 'U003', 1 FROM building b
WHERE b.code = 'B002' AND NOT EXISTS (SELECT 1 FROM unit u WHERE u.code = 'U003');

INSERT INTO unit (building_id, name, code, sort_order)
SELECT b.id, '2单元', 'U004', 2 FROM building b
WHERE b.code = 'B002' AND NOT EXISTS (SELECT 1 FROM unit u WHERE u.code = 'U004');

INSERT INTO unit (building_id, name, code, sort_order)
SELECT b.id, '1单元', 'U005', 1 FROM building b
WHERE b.code = 'B003' AND NOT EXISTS (SELECT 1 FROM unit u WHERE u.code = 'U005');

-- 快递柜：按楼栋/单元编码实时解析归属 ID，locker_no 唯一，INSERT IGNORE 跳过已存在编号
INSERT IGNORE INTO locker (locker_no, compartment_count, spec_type, building_id, unit_id, floor, installation_date, status)
SELECT 'KDG-001', 24, 'STANDARD', b.id, u.id, '1层', '2024-03-01', 'ACTIVE'
FROM building b JOIN unit u ON u.code = 'U001' AND u.building_id = b.id
WHERE b.code = 'B001';

INSERT IGNORE INTO locker (locker_no, compartment_count, spec_type, building_id, unit_id, floor, installation_date, status)
SELECT 'KDG-002', 36, 'LARGE', b.id, u.id, '1层', '2024-03-05', 'ACTIVE'
FROM building b JOIN unit u ON u.code = 'U002' AND u.building_id = b.id
WHERE b.code = 'B001';

INSERT IGNORE INTO locker (locker_no, compartment_count, spec_type, building_id, unit_id, floor, installation_date, status)
SELECT 'KDG-003', 18, 'SMALL', b.id, u.id, '1层', '2024-04-10', 'ACTIVE'
FROM building b JOIN unit u ON u.code = 'U003' AND u.building_id = b.id
WHERE b.code = 'B002';

INSERT IGNORE INTO locker (locker_no, compartment_count, spec_type, building_id, unit_id, floor, installation_date, status)
SELECT 'KDG-004', 30, 'MIXED', b.id, u.id, '1层', '2024-05-12', 'ACTIVE'
FROM building b JOIN unit u ON u.code = 'U005' AND u.building_id = b.id
WHERE b.code = 'B003';

-- 滞留件清柜单：按单号幂等补种；KDG-001 办理中（列表/详情显示滞留中），KDG-003 已办结
INSERT INTO clearance_order (order_no, locker_id, overdue_compartments, package_count, found_time, handler, status, remark)
SELECT 'QG20260901001', l.id, 'A03,A07', 2, '2026-09-01 09:30:00', '张师傅', 'PROCESSING', '超期3天未取，已电话通知业主'
FROM locker l
WHERE l.locker_no = 'KDG-001'
  AND NOT EXISTS (SELECT 1 FROM clearance_order c WHERE c.order_no = 'QG20260901001');

INSERT INTO clearance_order (order_no, locker_id, overdue_compartments, package_count, found_time, handler, status, handle_result, complete_time)
SELECT 'QG20260820001', l.id, 'B12', 1, '2026-08-20 15:00:00', '李管家', 'COMPLETED', '业主已取走滞留件，格口清空并消毒', '2026-08-22 10:00:00'
FROM locker l
WHERE l.locker_no = 'KDG-003'
  AND NOT EXISTS (SELECT 1 FROM clearance_order c WHERE c.order_no = 'QG20260820001');

-- 钥匙借用台账：按编号幂等补种；KDG-002 借用中（列表/详情显示借用中），KDG-004 已归还的历史记录
INSERT INTO key_borrow_record (record_no, locker_id, borrower, reason, borrow_time, expected_return_time, status, remark)
SELECT 'JY20260910001', l.id, '王维修', '柜门卡滞检修，取钥匙开柜排查', '2026-09-10 09:00:00', '2026-09-13 18:00:00', 'ON_LOAN', '维修期间钥匙由维修队保管'
FROM locker l
WHERE l.locker_no = 'KDG-002'
  AND NOT EXISTS (SELECT 1 FROM key_borrow_record k WHERE k.record_no = 'JY20260910001');

INSERT INTO key_borrow_record (record_no, locker_id, borrower, reason, borrow_time, expected_return_time, status, returner, return_time)
SELECT 'JY20260805001', l.id, '赵快递员', '批量投件临时借用柜门钥匙', '2026-08-05 14:00:00', '2026-08-05 18:00:00', 'RETURNED', '赵快递员', '2026-08-05 17:30:00'
FROM locker l
WHERE l.locker_no = 'KDG-004'
  AND NOT EXISTS (SELECT 1 FROM key_borrow_record k WHERE k.record_no = 'JY20260805001');

-- 电表抄表单：按单号幂等补种
-- KDG-001 本月(2026-09)已抄；KDG-002 本月已抄且留有一张已作废单（作废原因留存，作废后可重新登记）；
-- KDG-003 仅上月(2026-08)历史单，本月未抄；KDG-004 无记录，本月未抄
INSERT INTO meter_reading_record (record_no, locker_id, period_month, reading_value, reader, reading_time, status, remark)
SELECT 'CB20260905001', l.id, '2026-09', 1280.50, '李抄表', '2026-09-05 10:00:00', 'ACTIVE', '月度例行抄表'
FROM locker l
WHERE l.locker_no = 'KDG-001'
  AND NOT EXISTS (SELECT 1 FROM meter_reading_record m WHERE m.record_no = 'CB20260905001');

INSERT INTO meter_reading_record (record_no, locker_id, period_month, reading_value, reader, reading_time, status, void_reason, void_operator, void_time)
SELECT 'CB20260903001', l.id, '2026-09', 986.00, '王抄表', '2026-09-03 09:30:00', 'VOIDED', '读数录入错误，与实际表码不符', '系统管理员', '2026-09-03 15:20:00'
FROM locker l
WHERE l.locker_no = 'KDG-002'
  AND NOT EXISTS (SELECT 1 FROM meter_reading_record m WHERE m.record_no = 'CB20260903001');

INSERT INTO meter_reading_record (record_no, locker_id, period_month, reading_value, reader, reading_time, status, remark)
SELECT 'CB20260904001', l.id, '2026-09', 986.00, '王抄表', '2026-09-04 09:00:00', 'ACTIVE', '作废错单后重新登记'
FROM locker l
WHERE l.locker_no = 'KDG-002'
  AND NOT EXISTS (SELECT 1 FROM meter_reading_record m WHERE m.record_no = 'CB20260904001');

INSERT INTO meter_reading_record (record_no, locker_id, period_month, reading_value, reader, reading_time, status, remark)
SELECT 'CB20260806001', l.id, '2026-08', 745.20, '李抄表', '2026-08-06 10:30:00', 'ACTIVE', '上月例行抄表'
FROM locker l
WHERE l.locker_no = 'KDG-003'
  AND NOT EXISTS (SELECT 1 FROM meter_reading_record m WHERE m.record_no = 'CB20260806001');
