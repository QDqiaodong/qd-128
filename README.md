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
