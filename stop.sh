#!/bin/bash

set -e

echo "========================================"
echo "  居民小区快递柜管理系统 - 停止脚本"
echo "========================================"

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$SCRIPT_DIR"

echo ""
echo "停止 Docker Compose 服务..."

docker compose down

echo ""
echo "========================================"
echo "  服务已停止！"
echo "========================================"
