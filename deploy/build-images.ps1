# =====================================================================
# 本地一键构建 + 导出镜像脚本（在 Windows 的 PowerShell 中执行）
#
# 用法：右键本文件 -> "使用 PowerShell 运行"
#   或在 PowerShell 中执行：  .\deploy\build-images.ps1
#
# 执行内容：
#   1. 检查 Docker 环境
#   2. 若无 .env 则自动生成（含随机强数据库密码，已存在则复用）
#   3. 构建前端 / 后端 / MCP 三个应用镜像
#   4. 导出全部镜像 + compose 文件 + .env 到 deploy\release\ 目录
#
# 产物目录 deploy\release\ 共 4 个文件，全部上传到服务器：
#   lianba-ai-agent-images.tar   全部 Docker 镜像
#   docker-compose.prod.yml      生产 compose 文件
#   .env                         数据库密码等生产配置
#   install.sh                   服务器端一键安装脚本
# =====================================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $ProjectRoot
$ReleaseDir = Join-Path $ProjectRoot "deploy\release"

Write-Host ""
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "  恋吧 AI  -  生产镜像构建与导出" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host ""

# ---------- [1/5] 检查 Docker ----------
Write-Host "[1/5] 检查 Docker 环境..." -ForegroundColor Yellow
$dockerOk = $false
try {
    docker info *> $null
    if ($LASTEXITCODE -eq 0) { $dockerOk = $true }
} catch { $dockerOk = $false }

if (-not $dockerOk) {
    Write-Host "  [失败] Docker 未运行。请先启动 Docker Desktop，等待其完全就绪后重试。" -ForegroundColor Red
    exit 1
}
docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [失败] 未检测到 docker compose 命令，请升级 Docker Desktop。" -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Docker 环境正常" -ForegroundColor Green

# ---------- [2/5] 准备 .env（数据库密码） ----------
Write-Host ""
Write-Host "[2/5] 准备 .env 生产配置..." -ForegroundColor Yellow
$EnvFile = Join-Path $ProjectRoot ".env"
if (-not (Test-Path $EnvFile)) {
    # 生成 24 位随机强密码（仅字母数字，避免特殊字符转义问题）
    $chars = @()
    $chars += [char[]](48..57)   # 0-9
    $chars += [char[]](65..90)   # A-Z
    $chars += [char[]](97..122)  # a-z
    $dbPassword = -join (1..24 | ForEach-Object { $chars | Get-Random })
    $envContent = @(
        "# 生产环境配置（由 build-images.ps1 自动生成；含敏感信息，请勿提交到 git）",
        "DB_NAME=lianba_ai_agent",
        "DB_USERNAME=shenzhou_ruofeng",
        "DB_PASSWORD=$dbPassword"
    ) -join "`n"
    # 使用 ASCII 编码写入，避免 UTF-8 BOM 破坏 .env 解析
    [System.IO.File]::WriteAllText($EnvFile, $envContent + "`n", [System.Text.Encoding]::ASCII)
    Write-Host "  [OK] 已生成 .env（数据库密码为 24 位随机强密码）" -ForegroundColor Green
} else {
    Write-Host "  [OK] .env 已存在，直接复用（密码保持不变，与线上数据库一致）" -ForegroundColor Green
}

# ---------- [3/5] 拉取数据库基础镜像 ----------
Write-Host ""
Write-Host "[3/5] 拉取 PostgreSQL(pgvector) 基础镜像..." -ForegroundColor Yellow
docker pull pgvector/pgvector:pg16
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [失败] 基础镜像拉取失败。若是网络问题，请为 Docker Desktop 配置镜像加速后重试。" -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] pgvector/pgvector:pg16 就绪" -ForegroundColor Green

# ---------- [4/5] 构建三个应用镜像 ----------
Write-Host ""
Write-Host "[4/5] 构建应用镜像（首次构建需下载依赖，约 10~30 分钟，请耐心等待）..." -ForegroundColor Yellow
docker compose -f docker-compose.prod.yml build
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [失败] 镜像构建失败，请把上方红色报错内容发给技术同学排查。" -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] 三个应用镜像构建完成" -ForegroundColor Green

# ---------- [5/5] 导出发布包 ----------
Write-Host ""
Write-Host "[5/5] 导出发布包到 deploy\release\ ..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null

$imagesTar = Join-Path $ReleaseDir "lianba-ai-agent-images.tar"
if (Test-Path $imagesTar) { Remove-Item $imagesTar -Force }

docker save -o $imagesTar `
    pgvector/pgvector:pg16 `
    lianba-ai-agent-mcp-server:prod `
    lianba-ai-agent-backend:prod `
    lianba-ai-agent-frontend:prod
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [失败] 镜像导出失败" -ForegroundColor Red
    exit 1
}

Copy-Item (Join-Path $ProjectRoot "docker-compose.prod.yml") (Join-Path $ReleaseDir "docker-compose.prod.yml") -Force
Copy-Item $EnvFile (Join-Path $ReleaseDir ".env") -Force
Copy-Item (Join-Path $PSScriptRoot "install.sh") (Join-Path $ReleaseDir "install.sh") -Force

$tarSizeMB = [math]::Round((Get-Item $imagesTar).Length / 1MB, 1)
Write-Host "  [OK] 发布包已生成，镜像包大小：$tarSizeMB MB" -ForegroundColor Green

Write-Host ""
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "  构建完成！发布包位置：" -ForegroundColor Cyan
Write-Host "  $ReleaseDir" -ForegroundColor White
Write-Host ""
Write-Host "  下一步（详见《阿里云宝塔Docker部署手册》）：" -ForegroundColor Cyan
Write-Host "  1. 用 WinSCP / 宝塔文件管理器，把 release 目录里 4 个文件" -ForegroundColor White
Write-Host "     上传到服务器：/www/wwwroot/lianba-ai-agent/" -ForegroundColor White
Write-Host "  2. SSH 登录服务器执行：" -ForegroundColor White
Write-Host "     cd /www/wwwroot/lianba-ai-agent && bash install.sh" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Cyan
