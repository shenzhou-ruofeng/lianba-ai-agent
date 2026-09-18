# 一键启动全部三个服务（语音识别 + 后端 API + 前端）
# 依赖条件：
#   1. Python 已安装并加入 PATH
#   2. JDK21 已安装并加入 PATH
#   3. Maven 已安装并加入 PATH（或项目自带 mvnw.cmd）
#   4. PostgreSQL 与 pgvector 已运行（本地开发环境）
#
# 启动顺序：
#   1. faster-whisper 语音识别 WebSocket 服务（端口 10095）
#   2. Spring Boot 后端（端口 8123，prod profile）
#   3. Vite 前端（端口 3000，development）
#
# 注意：MCP 服务需手动先启动（端口 8127），否则后端无法启动成功

$PSScriptRoot = Split-Path $MyInvocation.MyCommand.Path -Parent

Write-Output "========================================"
Write-Output "恋吧 AI 三服务一键启动"
Write-Output "========================================"

# 1. 启动 faster-whisper 语音识别服务（后台）
Write-Output "[1/3] 正在启动 faster-whisper 语音识别服务..."
Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File '$PSScriptRoot\faster-whisper-server\run-whisper.ps1'" -WindowStyle Hidden -PassThru | Out-Null
Start-Sleep -Seconds 3

# 2. 启动 Spring Boot 后端（后台）
Write-Output "[2/3] 正在启动 Spring Boot 后端服务..."
cd "$PSScriptRoot"
Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -Command 'cd ''$PSScriptRoot''; .\mvnw.cmd spring-boot:run `" -Dspring-boot.run.profiles=prod`" -Dspring-boot.run.jvmArguments=`"-Dfile.encoding=UTF-8`"' -WindowStyle Hidden -PassThru | Out-Null
Start-Sleep -Seconds 5

# 3. 启动 Vite 前端（前台，可看到构建日志）
Write-Output "[3/3] 正在启动 Vite 前端服务..."
cd "$PSScriptRoot\lianba-ai-agent-frontend"
Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -Command 'cd ''$PSScriptRoot\lianba-ai-agent-frontend''; npm run dev'" -WindowStyle Normal -PassThru | Out-Null

Write-Output ""
Write-Output "========================================"
Write-Output "✅ 所有服务已启动！"
Write-Output "========================================"
Write-Output "📢 前端访问地址：http://localhost:3000"
Write-Output "🔧 后端 API 地址：http://localhost:8123/api"
Write-Output "🎙️ 语音识别服务：ws://127.0.0.1:10095"
Write-Output "⚠️  注意：MCP 服务（端口 8127）需手动先启动，否则后端可能无法连接工具调用"
Write-Output ""
Write-Output "提示：如需停止服务，请关闭对应的 PowerShell 窗口或使用 taskkill 命令。"
