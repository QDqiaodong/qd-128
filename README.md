# 居民小区快递柜楼栋单元多条件组合筛选归档系统

## 项目简介

本系统用于小区物业统一管理快递寄存柜，支持楼栋单元绑定、多条件组合筛选、筛选结果快照归档和历史留存。

## 技术栈

- 前端：Vue 3、Vite、TypeScript、Element Plus
- 后端：Spring Boot 3.3、JDK 17、Spring Data JPA、Redis
- 数据库：MySQL 8.0
- 部署：Docker Compose

## 端口说明

| 服务 | 地址或端口 |
| --- | --- |
| 前端访问地址 | http://localhost:8228 |
| 后端 API 地址 | http://localhost:8328/api |
| MySQL | 127.0.0.1:3528 |
| Redis | 127.0.0.1:6628 |

端口统一维护在根目录 `.env`，示例配置见 `.env.example`。

## 启动方式

```bash
cd /Users/Admin/Desktop/solo-0601/qd-0601/qd-组1/qd-128
docker compose up -d --build
```

## 单独编译验证

```bash
cd backend
mvn compile -q
```

```bash
cd frontend
npm ci
npm run build
```

## Docker 构建说明

Docker Compose 使用固定端口并绑定 `127.0.0.1`；前后端 Dockerfile 保留依赖缓存层，便于定位后端编译、前端构建、Docker 编排和运行访问链路。

## 常见问题

- 后端编译失败时先执行 `mvn -version` 检查 JDK，再检查 Lombok、Maven 编译插件和 `pom.xml` 是否被忽略。
- 前端构建失败时优先按实际报错检查 import 路径、导出名、Vite 代理端口和 TypeScript 构建错误。
- 页面中文乱码时检查源码、SQL 初始化脚本、数据库字符集、连接串编码和已有 Docker volume 数据；初始化 SQL 已增加 `SET NAMES utf8mb4;`。

## 种子数据与补种机制

系统预置种子数据：3 栋楼（B001-B003）、5 个单元（U001-U005）、4 台编号快递柜（KDG-001 ~ KDG-004）。

- `schema.sql` 仅在 MySQL 数据卷**首次为空**时由容器自动执行一次；所有种子语句均已幂等化（`INSERT IGNORE` / 按编码判重），重复执行或手动补种不会产生重复数据，也不会因楼栋已存在而中断后续柜体种子。
- 后端每次启动时执行 `SeedDataInitializer`，按编码逐条检查并**只补缺失**的种子记录（不修改已有数据），随后刷新楼栋/单元缓存。因此旧数据卷（只有楼栋单元、缺柜体种子）在后端升级重启后会自动补齐柜体，快递柜列表与可巡检范围随即恢复一致。
- 如需手动补种，可执行：`docker exec -i locker-mysql mysql -ulocker -p<密码> locker_db < backend/src/main/resources/schema.sql`。
- 如需完全重置（清空全部业务数据并重新初始化）：`docker compose down -v && docker compose up -d --build`。

## 柜体生命周期状态

每个快递柜具有可追踪的生命周期状态（`locker.status`），共三种：

| 状态码 | 含义 | 说明 |
| --- | --- | --- |
| `ACTIVE` | 正常 | 新建柜体的默认状态 |
| `TEMPORARILY_DISABLED` | 临时停用 | 可恢复为正常，也可转为永久停用 |
| `PERMANENTLY_DISABLED` | 永久停用 | 终态，不可再恢复或变更 |

合法流转：正常 → 临时停用 / 永久停用；临时停用 → 正常（恢复）/ 永久停用。
管理员可在「快递柜列表」行内或「快递柜详情」页发起变更，必须填写变更原因和操作人（操作人留空默认“系统管理员”）。系统在 `status_change_record` 表保存变更前后状态、原因、操作人和变更时间。详情页展示完整变更记录并支持按“变更后状态”筛选。

多条件筛选（`POST /api/lockers/filter`）默认只返回 `ACTIVE` 柜体，停用柜体不出现在结果中；显式传入 `statuses` 可包含停用柜体。列表接口 `GET /api/lockers?statuses=...` 为管理视角，不传时返回全部状态。

归档（快照）时会在 `archive_item.status_snapshot` 冻结每个柜体当时的状态；归档详情同时展示“归档时状态”和“当前状态”，柜体之后被停用或恢复不影响历史快照。

相关接口：

- `POST /api/lockers/{id}/status`：变更状态，body 为 `{ targetStatus, reason, operator }`
- `GET  /api/lockers/{id}/status-changes?status=`：查询完整状态变更记录，可按状态筛选
- `GET  /api/lockers/statuses`：获取状态码与中文名映射

## 物业层级维护（楼栋 / 单元）

「物业层级管理」页面（`/hierarchy`）以树形结构维护楼栋与单元，支持新增、改名、编码调整和删除。

- 楼栋编码全局唯一；单元编码在同一楼栋下唯一（均可留空）。
- 删除前系统检查关联快递柜及归档快照：
  - `GET /api/buildings/{id}/references` / `GET /api/units/{id}/references` 返回 `{ lockerCount, archiveCount, unitCount, deletable }`。
  - 存在关联快递柜（`lockerCount > 0`）或归档快照（`archiveCount > 0`）时禁止删除，前端展示受影响数量。
  - 楼栋无关联时，其下单元随楼栋一并删除。
- 名称或编码只保存在 `building`/`unit` 表，快递柜列表、详情、多条件筛选结果、归档快照和归属调整历史均通过 ID 实时关联解析，改名或调整编码后各页面自动显示最新层级信息。

## 物业巡检模块

「物业巡检」页面（`/inspections`）供管理员按楼栋/单元发起快递柜巡检任务，并逐台登记格口、屏幕、门锁等检查项。

### 发起任务

- 在「发起巡检任务」页选择**楼栋**（必填）与**单元**（不选则巡检整栋楼），系统按发起时刻该范围内状态为 `ACTIVE` 的正常快递柜逐台冻结生成巡检明细；临时停用、永久停用柜不会进入新任务，之后柜体归属调整或状态变更不影响任务已冻结的柜体关联关系。
- 选择楼栋/单元后实时刷新应检台数与可选柜体清单，停用柜仍可在快递柜列表和详情中查看档案；所选范围内没有正常快递柜时不允许发起。即使请求被篡改为传入停用柜 ID，后端也会再次过滤，不生成对应待检行。
- 可设置**巡检周期**（一次性/每日/每周/每月）、**负责人**、**截止时间**和创建人。

### 逐台巡检与异常记录

- 任务详情中对每台快递柜的**格口、屏幕、门锁**分别登记「正常 / 异常 / 不适用」，可填备注和巡检人，逐台保存。
- 任一检查项标记为**异常**时自动生成一条「待处理」异常记录（`inspection_issue`）；复检恢复正常时，仍处于待处理的记录会自动关闭，已在处理中/已解决的记录保留处理痕迹。同一检查项在存在未关闭记录时不会重复生成。
- 异常记录支持「开始处理」「标记解决」，并记录处理人、处理说明和处理时间，可在任务详情中按处理状态筛选。

### 任务进度与筛选

- 任务进度（已检/应检台数与百分比）、异常柜体数、待处理异常数全部以巡检明细表和异常表为唯一数据源，由后端在每次提交/处理后统一重算，**刷新页面后保持一致**。
- 列表支持按**未完成**（待开始 + 进行中）、**逾期**（已过截止时间且未完成）、**异常**（存在待处理异常）筛选，并可按任务名称/负责人关键字搜索。
- 任务状态：待开始 → 进行中 → 已完成（全部柜体完成填报）；截止时间已过但未完成的任务展示为「已逾期」。

### 数据一致性

- 巡检任务仅以 ID 关联楼栋/单元（不建外键），层级改名后任务与详情实时显示最新名称；仍被巡检任务引用的楼栋/单元禁止删除（见「物业层级管理」删除前校验）。
- 删除巡检任务会一并删除其巡检明细与异常记录。

相关接口（前缀 `/api/inspections`）：

- `GET  /api/inspections/scope?buildingId=&unitId=`：实时查询楼栋/单元下可纳入新巡检任务的正常柜及应检台数
- `POST /api/inspections`：发起任务，body 含 `taskName, buildingId, unitId?, cycle, assignee?, deadline?, creator?, lockerIds?`；`lockerIds` 仍会按范围和正常状态二次过滤
- `GET  /api/inspections?status=&keyword=&overdue=&abnormal=`：分页任务列表，`status=INCOMPLETE` 表示未完成
- `GET  /api/inspections/{id}` / `DELETE /api/inspections/{id}`：任务详情 / 删除
- `GET  /api/inspections/{id}/records`：任务下逐台柜体的检查项明细
- `POST /api/inspections/{id}/submit`：提交逐台检查结果（body 为 `{ items: [{ lockerId, compartmentResult, screenResult, lockResult, remark?, inspector? }] }`，结果取值 `NORMAL/ABNORMAL/NOT_APPLICABLE`）
- `GET  /api/inspections/{id}/issues?status=`：异常待处理记录，可按 `PENDING/PROCESSING/RESOLVED` 筛选
- `PUT  /api/inspections/issues/{issueId}`：更新异常处理状态，body 为 `{ status, handler?, handleNote? }`
- `GET  /api/inspections/cycles` / `GET  /api/inspections/task-statuses`：周期、任务状态字典

## 钥匙借用台账模块

「钥匙借用台账」页面（`/key-borrows`）供物业登记快递柜柜门钥匙的临时借用与归还。

### 借出登记

- 登记时必须填写**借出人**、**借用事由**、**借出时间**和**预计归还时间**；借出时间不能晚于当前时间，预计归还时间不能早于借出时间。
- **同一柜钥匙未还清前不能再借出**：该柜存在「借用中」记录时，登记下拉框中对应柜体置灰，后端也会再次拦截。
- 整个登记在单个事务内一次落库，任一校验不通过整体回滚，**取消登记不会写出半条台账**；前端登记窗口未提交前关闭仅丢弃草稿，不产生任何数据。
- 永久停用柜、临时停用柜同样可选，用于**补登历史借用**（借出时间可选过去时间）。

### 归还登记

- 归还必须填写**归还人**；**归还时间**默认当前时间，补登历史归还时可指定过去时间，但不能早于借出时间、不能晚于当前时间。
- 已归还的记录不能重复归还。

### 借用改期

- 钥匙未还、**预计归还刚好到点或已过点时**，即可在原借用单上**改一个更晚的预计归还时间**，并必须写明**改期原因**；系统在原单上累计改期次数、留存最近一次改期原因与改期时间。
- 新的预计归还时间必须晚于原预计归还时间；**已归还的单不能改期**，**还没到预计归还时间（未来时间）的单会被拦下并提示「还没到预计归还时间，不能改期」**（列表中该单的「改期」按钮置灰，悬停可见同样提示）。
- 整个改期在单个事务内一次落库，任一校验不通过整体回滚；改期窗口未提交前关闭仅丢弃草稿，**不会留下半条改期**。
- 改期只更新预计归还时间与改期痕迹，不改变借用状态：刷新后台账列表的预计归还、是否逾期与柜体「借用中」标记保持一致，按柜一览的未还条数不会因改期减少。

### 交接班

- 交班人在借用台账页点「交接班」打开交接窗，窗口内列出**提交时刻全部「借用中」的柜**（与台账、按柜一览同源），交班人逐柜勾选点名，并填写**交班人、接班人、交接说明**（均必填）。
- **必须勾齐全部未还柜才能提交**：勾漏（含重复点名）或勾选了已归还/不存在的单都会被拦下并提示，后端在提交事务内以台账实时状态重新核对，窗口打开期间发生新借出/归还也不会按旧名单误交。
- 整个交接（交接主记录 + 每柜点名明细）在单个事务内一次落库，任一校验不通过整体回滚；**交接窗未提交前关闭只丢弃草稿，不会留下半次交接**（已勾选/填写时关闭会二次确认）。
- 交接只转移钥匙保管责任、在台账里**留存交接痕迹**（编号、交班人、接班人、说明、点名柜数与逐柜明细），**不改变借用状态**：交接后按柜一览仍显示「借用中」、未还条数不变，柜体列表/详情的借用中标记不变；台账列表对被点名的单显示「已交接×N」可查看交接痕迹，柜详情另设「钥匙交接痕迹」卡片。
- 借用标记与未还条数始终以 `key_borrow_record` 为唯一数据源实时推导，刷新后台账列表、按柜一览、柜详情标记与未还条数对得上。

### 标记与一致性

- 借用中的柜体在「快递柜列表」「快递柜详情」均显示「借用中」标记，详情页另设「钥匙借用记录」卡片留存该柜全部借用历史。
- 借用标记、未还条数全部以 `key_borrow_record` 表为唯一数据源实时推导，**刷新页面后台账列表、按柜一览与柜体展示保持一致**。
- 「按柜钥匙状态」一览列出全部柜体（含停用柜），借用中的柜体置顶，并汇总当前未还总条数；借用中且预计归还时间已过的记录标记「逾期未还」。

相关接口（前缀 `/api/key-borrows`）：

- `POST /api/key-borrows`：登记借用，body 为 `{ lockerId, borrower, reason, borrowTime, expectedReturnTime, remark? }`
- `GET  /api/key-borrows?status=&lockerId=&keyword=`：分页台账列表，`status=ON_LOAN/RETURNED`
- `GET  /api/key-borrows/{id}`：台账详情
- `POST /api/key-borrows/{id}/return`：归还登记，body 为 `{ returner, returnTime? }`，归还人必填
- `POST /api/key-borrows/{id}/extend`：借用改期，body 为 `{ expectedReturnTime, extendReason }`，借用中且预计归还刚好到点或已过点可改，新预计归还必须更晚，改期原因必填；未到点改期返回 400「还没到预计归还时间，不能改期」
- `GET  /api/key-borrows/locker/{lockerId}`：某台柜体的全部借用记录
- `GET  /api/key-borrows/locker-overview`：按柜钥匙状态一览（含未还条数）
- `GET  /api/key-borrows/locker-options`：登记可选柜体（含永久停用柜）
- `GET  /api/key-borrows/statuses`：借用状态字典

交接班接口（前缀 `/api/key-handovers`）：

- `GET  /api/key-handovers/pending`：交接窗待点名清单（当前全部借用中记录，实时推导）
- `POST /api/key-handovers`：提交交接，body 为 `{ handoverFrom, handoverTo, handoverNote, recordIds }`；交班人/接班人/交接说明必填，`recordIds` 必须与提交时刻全部未还记录逐一对齐，勾漏或勾多返回 400，事务整体回滚不留半条
- `GET  /api/key-handovers?page=&size=`：交接痕迹分页
- `GET  /api/key-handovers/{id}`：交接详情（含逐柜点名明细）
- `GET  /api/key-handovers/by-record/{recordId}`：某条借用记录的交接痕迹
- `GET  /api/key-handovers/by-locker/{lockerId}`：某台柜体的交接痕迹

## 电表抄表模块

「电表抄表」页面（`/meter-readings`）供物业按柜登记每月电表读数、抄表人和抄表时间，并维护抄表单的作废痕迹。

### 登记抄表

- 登记时必须填写**柜体**、**电表读数**（不能为负）、**抄表人**和**抄表时间**；抄表时间不能晚于当前时间，补登历史月份可选过去时间。
- **账期（自然月）由抄表时间自动推导**，无需手工选择，保证同一自然月的单据账期口径一致。
- **同一柜同一自然月不能同时挂两张未作废抄表单**：该柜该月已存在有效单时，登记下拉框中对应柜体置灰并标记「本月已抄」，后端也会再次拦截并提示先作废原单；已作废的单不占用该月额度，作废后可重新登记。
- 整个登记在单个事务内一次落库，任一校验不通过整体回滚，**关闭抄表窗未提交不会写出半张单据**；前端抄表窗口未提交前关闭仅丢弃草稿（已填写内容时关闭需二次确认），不产生任何数据。

### 作废抄表单

- 作废**必须填写作废原因**，可填作废人（留空默认“系统管理员”）；系统留存作废原因、作废人和作废时间。
- 已作废的单不能重复作废；作废后该柜该月不再占用有效单额度，可重新登记。

### 已抄 / 未抄标记与一致性

- 「按柜本月抄表」一览列出全部柜体（含停用柜），未抄的柜体置顶，并汇总**本月已抄台数**；支持按已抄/未抄过滤。
- 「快递柜列表」「快递柜详情」均显示「本月已抄 / 本月未抄」标记，详情页另设「电表抄表记录」卡片留存该柜全部抄表历史（含已作废单），并在基本信息中展示**本月电表读数**、抄表人和抄表时间。
- 已抄/未抄标记、本月已抄台数、柜体页读数全部以 `meter_reading_record` 表的有效（未作废）单为唯一数据源实时推导，**刷新页面后一览台数、柜体页读数和筛选结果保持一致**。

相关接口（前缀 `/api/meter-readings`）：

- `POST /api/meter-readings`：登记抄表，body 为 `{ lockerId, readingValue, reader, readingTime, remark? }`，账期由抄表时间推导
- `GET  /api/meter-readings?page=&size=&periodMonth=&status=&lockerId=&keyword=`：分页抄表单列表，`status=ACTIVE/VOIDED`，`periodMonth` 格式 `yyyy-MM`
- `GET  /api/meter-readings/{id}`：抄表单详情
- `POST /api/meter-readings/{id}/void`：作废抄表单，body 为 `{ voidReason, voidOperator? }`，作废原因必填
- `GET  /api/meter-readings/locker/{lockerId}`：某台柜体的全部抄表单
- `GET  /api/meter-readings/locker-overview?periodMonth=`：按柜抄表状态一览（默认当前自然月，含本月读数）
- `GET  /api/meter-readings/locker-options`：登记可选柜体（本月已抄的柜体标记置灰）
- `GET  /api/meter-readings/statuses`：抄表单状态字典

## 清柜单当面催领模块

办理中的清柜单（滞留件）可逐笔登记「当面催领」，记录**催领时间**与**经办人**，物业上门当面催促业主取件后留痕。

### 登记与关闭

- 只有**办理中**的清柜单能登记催领；催领时间默认当前时间，可补登过去时间（不能晚于当前时间、不能早于清柜单发现时间），经办人必填。
- **同一张办理中的单不能同时挂两笔未关闭催领**：已有未关闭催领时，列表与催领窗均提示「催领中」，登记入口只会展示当前未关闭催领，后端在单事务内对清柜单加行锁再次拦截；关闭当前催领后才能再记下一笔。
- 催领可人工关闭（可填关闭说明与关闭经办人，经办人留空默认“系统管理员”）；**清柜单办结时，未关闭的催领在办结同一事务内自动关闭**并留痕「清柜单办结，系统自动关闭未关闭催领」，不会出现单据已办结却仍挂着未关闭催领。
- 登记/关闭都在单个事务内一次落库，任一校验不通过整体回滚；**关掉催领窗未提交不会写出半条**（已填写经办人或关闭内容时关闭需二次确认），前端窗口未提交前关闭仅丢弃草稿。

### 次数、状态与滞留标记一致性

- 清柜单列表展示催领次数与「催领中」标记，详情页展示完整当面催领台账；柜体详情页「滞留清柜记录」同样展示催领次数/催领中。
- 催领次数、是否存在未关闭催领全部以 `clearance_urge_record` 表为唯一数据源实时推导，**催领不改变清柜单状态，也不改变柜体「滞留中」标记**（滞留中仍只取决于是否存在办理中清柜单）；提交/关闭/办结后各页面统一以后端数据刷新，**刷新页面后催领次数、清柜单状态和柜体滞留中标记保持对得上**。

相关接口（前缀 `/api/clearances`）：

- `POST /api/clearances/{id}/urges`：登记一笔当面催领，body 为 `{ urgeTime?, operator }`，已有未关闭催领或单据已办结返回 400
- `POST /api/clearances/urges/{urgeId}/close`：关闭未关闭催领，body 为 `{ closeNote?, closeOperator? }`
- `GET  /api/clearances/{id}/urges`：某张清柜单的当面催领台账（按催领时间倒序）
- 清柜单 DTO 新增 `urgeCount`（累计次数）、`openUrge`（是否有未关闭催领），详情接口另返回 `urgeRecords`

