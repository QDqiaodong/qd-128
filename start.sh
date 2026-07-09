#!/bin/bash

set -e

echo "========================================"
echo "  居民小区快递柜管理系统 - 启动脚本"
echo "========================================"

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$SCRIPT_DIR"

echo ""
echo "1. 检查端口占用情况..."

FRONTEND_PORT=$(grep FRONTEND_PORT .env | cut -d'=' -f2)
BACKEND_PORT=$(grep BACKEND_PORT .env | cut -d'=' -f2)
MYSQL_PORT=$(grep MYSQL_PORT .env | cut -d'=' -f2)
REDIS_PORT=$(grep REDIS_PORT .env | cut -d'=' -f2)

PORT_CHECK=$(lsof -i ":$FRONTEND_PORT" ":$BACKEND_PORT" ":$MYSQL_PORT" ":$REDIS_PORT" 2>/dev/null | grep LISTEN | wc -l)

if [ $PORT_CHECK -gt 0 ]; then
    echo "警告：检测到端口被占用，正在停止占用进程..."
    lsof -ti ":$FRONTEND_PORT" ":$BACKEND_PORT" ":$MYSQL_PORT" ":$REDIS_PORT" 2>/dev/null | xargs -r kill -9 2>/dev/null || true
    sleep 2
fi

echo ""
echo "2. 启动 Docker Compose..."

docker compose up -d --build

echo ""
echo "3. 等待服务启动..."

MAX_WAIT=60
WAIT_COUNT=0

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if curl -s http://localhost:$BACKEND_PORT/api/buildings > /dev/null 2>&1; then
        break
    fi
    if curl -s http://localhost:$FRONTEND_PORT > /dev/null 2>&1; then
        break
    fi
    echo -n "."
    sleep 2
    WAIT_COUNT=$((WAIT_COUNT + 2))
done

echo ""
echo ""
echo "========================================"
echo "  服务启动完成！"
echo "========================================"
echo ""
echo "前端地址：http://localhost:$FRONTEND_PORT"
echo "后端地址：http://localhost:$BACKEND_PORT"
echo "MySQL端口：$MYSQL_PORT"
echo "Redis端口：$REDIS_PORT"
echo ""
echo "========================================"
