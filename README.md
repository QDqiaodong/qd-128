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
