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

-- 当面催领台账：办理中的清柜单可逐笔登记催领时间与经办人；
-- 同一张单同时只能挂一笔未关闭催领（函数唯一索引兜底，已关闭记录不占额度），
-- 单据办结时未关闭催领在同一事务内自动关闭。
CREATE TABLE IF NOT EXISTS clearance_urge_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '清柜单ID',
    urge_time DATETIME NOT NULL COMMENT '催领时间',
    operator VARCHAR(50) NOT NULL COMMENT '经办人(当面催领人)',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '催领状态: OPEN-未关闭, CLOSED-已关闭',
    close_note TEXT COMMENT '关闭说明',
    close_operator VARCHAR(50) COMMENT '关闭经办人',
    close_time DATETIME COMMENT '关闭时间',
    auto_closed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '办结时系统自动关闭: 1-是, 0-否',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_clearance_urge_order (order_id),
    INDEX idx_clearance_urge_status (status),
    UNIQUE KEY uk_clearance_urge_open ((CASE WHEN status = 'OPEN' THEN order_id END)),
    FOREIGN KEY (order_id) REFERENCES clearance_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清柜单当面催领记录表';

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

-- 钥匙交接班：交班人点名当前全部未还柜并填写接班人、交接说明后一次提交；
-- 交接只留保管责任转移痕迹，不改变借用记录状态，按柜一览未还条数不变。
CREATE TABLE IF NOT EXISTS key_handover (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    handover_no VARCHAR(40) NOT NULL UNIQUE COMMENT '交接单号',
    handover_from VARCHAR(50) NOT NULL COMMENT '交班人',
    handover_to VARCHAR(50) NOT NULL COMMENT '接班人',
    handover_note VARCHAR(500) NOT NULL COMMENT '交接说明',
    item_count INT NOT NULL COMMENT '本次点名未还柜数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钥匙交接班记录表';

-- 交接点名明细：一条对应提交时点名的一条借用中台账，仅按 ID 关联、不加外键，
-- 避免柜体/台账生命周期变更影响历史交接痕迹留存。
CREATE TABLE IF NOT EXISTS key_handover_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    handover_id BIGINT NOT NULL COMMENT '交接记录ID',
    record_id BIGINT NOT NULL COMMENT '借用台账记录ID',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    locker_no VARCHAR(50) COMMENT '点名时柜体编号快照',
    borrower_snapshot VARCHAR(50) COMMENT '点名时借出人快照',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_handover_item_handover (handover_id),
    INDEX idx_handover_item_record (record_id),
    INDEX idx_handover_item_locker (locker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钥匙交接班点名明细表';

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

-- 格口报修台账：柜详情登记故障格口、故障现象和报修人后建单（处理中）；
-- 完工填写处理人和处理结果后状态变为已修好。
-- 柜详情报修条数与柜体可用标记均以本表为唯一数据源实时推导。
CREATE TABLE IF NOT EXISTS repair_ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_no VARCHAR(40) NOT NULL UNIQUE COMMENT '报修单号',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    compartment_no VARCHAR(20) NOT NULL COMMENT '故障格口编号',
    symptom VARCHAR(500) NOT NULL COMMENT '故障现象',
    reporter VARCHAR(50) NOT NULL COMMENT '报修人',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '报修状态: PROCESSING-处理中, FIXED-已修好',
    handler VARCHAR(50) COMMENT '处理人(完工必填)',
    repair_result VARCHAR(500) COMMENT '处理结果(完工必填)',
    fixed_time DATETIME COMMENT '完工时间',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_repair_ticket_locker (locker_id),
    INDEX idx_repair_ticket_status (status),
    FOREIGN KEY (locker_id) REFERENCES locker(id)
    -- 同一柜同一格口只允许一条处理中报修单，由后端在建单事务内校验拦截
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递柜格口报修台账表';

-- 柜门未关告警台账：巡柜发现取件后柜门虚掩/未关严时登记（未处理），
-- 超过约定分钟仍未关严的柜必须能在本台账中查到；
-- 确认柜门已关严后同一条记录状态变为已关闭，柜体「柜门未关」标记随之恢复。
-- 柜体列表/详情的未关标记均以本表为唯一数据源实时推导。
CREATE TABLE IF NOT EXISTS door_alarm_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_no VARCHAR(40) NOT NULL UNIQUE COMMENT '告警编号',
    locker_id BIGINT NOT NULL COMMENT '快递柜ID',
    door_open_time DATETIME NOT NULL COMMENT '发现柜门未关时间',
    threshold_minutes INT NOT NULL DEFAULT 10 COMMENT '约定关严分钟数，超过仍未关严即为超时告警',
    reporter VARCHAR(50) NOT NULL COMMENT '上报人(巡柜发现人)',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '告警状态: OPEN-未处理, CLOSED-已关闭',
    close_operator VARCHAR(50) COMMENT '确认关闭人',
    close_time DATETIME COMMENT '确认关闭时间',
    close_note VARCHAR(500) COMMENT '关闭说明',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_door_alarm_locker (locker_id),
    INDEX idx_door_alarm_status (status),
    UNIQUE KEY uk_door_alarm_open ((CASE WHEN status = 'OPEN' THEN locker_id END)),
    FOREIGN KEY (locker_id) REFERENCES locker(id)
    -- 同一柜同时只允许一条未处理告警（函数唯一索引兜底，已关闭记录不占额度），
    -- 后端在登记事务内同样校验拦截
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递柜柜门未关告警台账表';

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

-- 格口报修台账：按单号幂等补种；KDG-001 处理中（列表/详情显示维修中），KDG-003 已修好的历史记录
INSERT INTO repair_ticket (ticket_no, locker_id, compartment_no, symptom, reporter, status, remark)
SELECT 'BX20260911001', l.id, '5', '5号格口门磁失灵，关门后指示灯不亮', '业主刘先生', 'PROCESSING', '已通知维保单位安排上门'
FROM locker l
WHERE l.locker_no = 'KDG-001'
  AND NOT EXISTS (SELECT 1 FROM repair_ticket r WHERE r.ticket_no = 'BX20260911001');

INSERT INTO repair_ticket (ticket_no, locker_id, compartment_no, symptom, reporter, status, handler, repair_result, fixed_time)
SELECT 'BX20260825001', l.id, '12', '12号格口锁具卡顿，柜门无法弹开', '快递员小周', 'FIXED', '张师傅', '更换锁芯并调试，开关恢复正常', '2026-08-26 16:00:00'
FROM locker l
WHERE l.locker_no = 'KDG-003'
  AND NOT EXISTS (SELECT 1 FROM repair_ticket r WHERE r.ticket_no = 'BX20260825001');

-- 柜门未关告警：按编号幂等补种；KDG-002 未处理且已超约定分钟（列表/详情显示柜门未关、台账超时标记），KDG-004 已确认关闭的历史记录
INSERT INTO door_alarm_record (alarm_no, locker_id, door_open_time, threshold_minutes, reporter, status, remark)
SELECT 'MJ20260913001', l.id, '2026-09-13 08:30:00', 10, '物业巡柜-老周', 'OPEN', '早高峰取件后柜门虚掩，巡柜发现上报'
FROM locker l
WHERE l.locker_no = 'KDG-002'
  AND NOT EXISTS (SELECT 1 FROM door_alarm_record d WHERE d.alarm_no = 'MJ20260913001');

INSERT INTO door_alarm_record (alarm_no, locker_id, door_open_time, threshold_minutes, reporter, status, close_operator, close_time, close_note)
SELECT 'MJ20260905001', l.id, '2026-09-05 19:10:00', 10, '物业巡柜-老周', 'CLOSED', '系统管理员', '2026-09-05 19:26:00', '现场核实柜门已关严，格口无遗留件'
FROM locker l
WHERE l.locker_no = 'KDG-004'
  AND NOT EXISTS (SELECT 1 FROM door_alarm_record d WHERE d.alarm_no = 'MJ20260905001');
