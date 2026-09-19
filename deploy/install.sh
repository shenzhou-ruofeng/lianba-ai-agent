#!/bin/bash
# =====================================================================
# 服务器端一键安装脚本（阿里云 + 宝塔面板）
#
# 使用前提（与下面文件放在同一目录）：
#   lianba-ai-agent-images.tar   从本地导出的镜像包
#   docker-compose.prod.yml      生产 compose 文件
#   .env                         数据库密码等配置
#
# 用法：
#   cd /www/wwwroot/lianba-ai-agent
#   bash install.sh
# =====================================================================
set -e
cd "$(dirname "$0")"

echo ""
echo "======================================================"
echo "  恋吧 AI  -  服务器端一键安装"
echo "======================================================"
echo ""

# ---------- [1/5] 检查 Docker 环境 ----------
echo "[1/5] 检查 Docker 环境..."
if ! command -v docker >/dev/null 2>&1; then
    echo "  [失败] 未安装 Docker。请先在宝塔面板 -> 软件商店 安装「Docker管理器」，"
    echo "         或联系我获取 Docker 安装命令。"
    exit 1
fi

COMPOSE=""
if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
else
    echo "  [失败] 未检测到 Docker Compose。"
    echo "         宝塔 Docker 管理器较新版本自带 compose，请升级 Docker 后重试。"
    exit 1
fi
echo "  [OK] Docker 环境正常（compose 命令：$COMPOSE）"

# ---------- [2/5] 检查文件完整性 ----------
echo ""
echo "[2/5] 检查安装文件..."
for f in "lianba-ai-agent-images.tar" "docker-compose.prod.yml" ".env"; do
    if [ ! -f "$f" ]; then
        echo "  [失败] 缺少文件：$f（请确认 3 个文件都已上传到当前目录）"
        exit 1
    fi
done
echo "  [OK] 安装文件齐全"

# ---------- [3/5] 导入镜像 ----------
echo ""
echo "[3/5] 导入 Docker 镜像（约 1~3 分钟，请耐心等待）..."
docker load -i lianba-ai-agent-images.tar
echo "  [OK] 镜像导入完成"

# ---------- [4/5] 启动全部服务 ----------
echo ""
echo "[4/5] 启动全部服务（PostgreSQL -> MCP -> 后端 -> 前端）..."
mkdir -p tmp
$COMPOSE -f docker-compose.prod.yml up -d

# ---------- [5/5] 等待就绪并检查 ----------
echo ""
echo "[5/5] 等待服务启动（后端为 Java 应用，约需 30~90 秒）..."
ok=0
for i in $(seq 1 30); do
    sleep 5
    if command -v curl >/dev/null 2>&1; then
        resp=$(curl -s --max-time 5 http://127.0.0.1:8090/api/health || true)
    else
        resp=$(wget -q -O - --timeout=5 http://127.0.0.1:8090/api/health || true)
    fi
    if echo "$resp" | grep -q '"ok"'; then
        ok=1
        break
    fi
    echo "  ...第 ${i} 次检查（共 30 次），服务尚未就绪，继续等待"
done

echo ""
$COMPOSE -f docker-compose.prod.yml ps
echo ""
if [ "$ok" = "1" ]; then
    echo "======================================================"
    echo "  安装成功！后端健康检查已通过。"
    echo ""
    echo "  本机验证地址：http://127.0.0.1:8090 （如安全组临时放行 8090 可直接用公网IP访问验证）"
    echo ""
    echo "  下一步："
    echo "  1. 在宝塔面板 -> 网站，添加站点并配置反向代理到 http://127.0.0.1:8090"
    echo "  2. 申请 SSL 证书并开启强制 HTTPS"
    echo "  3. 浏览器访问 https://你的域名 完成验收（详见部署手册第 4 章）"
    echo "======================================================"
else
    echo "======================================================"
    echo "  [注意] 服务已启动，但健康检查暂未通过。"
    echo "  常见原因：后端仍在初始化 / 内存不足 / 端口被占用。"
    echo ""
    echo "  请执行以下命令查看日志排查："
    echo "    $COMPOSE -f docker-compose.prod.yml logs --tail=100 backend"
    echo "    $COMPOSE -f docker-compose.prod.yml logs --tail=50 mcp-server"
    echo "    free -h        # 查看内存/交换分区"
    echo "======================================================"
    exit 1
fi
