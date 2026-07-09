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
