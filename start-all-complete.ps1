# 一键启动全部四个服务（MCP + 语音识别 + 后端 API + 前端）
# 依赖条件：
#   1. Python 已安装并加入 PATH
#   2. JDK21 已安装并加入 PATH
#   3. Maven 已安装并加入 PATH（或项目自带 mvnw.cmd）
#   4. PostgreSQL 与 pgvector 已运行（本地开发环境）
#
# 启动顺序：
#   1. MCP 图片搜索服务（端口 8127）- 必须先启动，后端依赖它
#   2. faster-whisper 语音识别 WebSocket 服务（端口 10095）
#   3. Spring Boot 后端（端口 8123，prod profile）
#   4. Vite 前端（端口 3000，development）
#
# 注意：这是最完整的启动方式，推荐首次使用时使用

$PSScriptRoot = Split-Path $MyInvocation.MyCommand.Path -Parent

Write-Output "========================================"
Write-Output "恋吧 AI 四服务一键启动（完整模式）"
Write-Output "========================================"

# 0. 启动 MCP 图片搜索服务（后台，必须先于后端启动）
Write-Output "[0/4] 正在启动 MCP 图片搜索服务..."
Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File '$PSScriptRoot\start-mcp-server.ps1'" -WindowStyle Hidden -PassThru | Out-Null
Start-Sleep -Seconds 5

# 1. 启动 faster-whisper 语音识别服务（后台）
Write-Output "[1/4] 正在启动 faster-whisper 语音识别服务..."
Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File '$PSScriptRoot\faster-whisper-server\run-whisper.ps1'" -WindowStyle Hidden -PassThru | Out-Null
Start-Sleep -Seconds 3

# 2. 启动 Spring Boot 后端（后台）
Write-Output "[2/4] 正在启动 Spring Boot 后端服务..."
cd "$PSScriptRoot"
Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -Command 'cd ''$PSScriptRoot''; .\mvnw.cmd spring-boot:run `" -Dspring-boot.run.profiles=prod`" -Dspring-boot.run.jvmArguments=`"-Dfile.encoding=UTF-8`"' -WindowStyle Hidden -PassThru | Out-Null
Start-Sleep -Seconds 5

# 3. 启动 Vite 前端（前台，可看到构建日志）
Write-Output "[3/4] 正在启动 Vite 前端服务..."
cd "$PSScriptRoot\lianba-ai-agent-frontend"
Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -Command 'cd ''$PSScriptRoot\lianba-ai-agent-frontend''; npm run dev'" -WindowStyle Normal -PassThru | Out-Null

Write-Output ""
Write-Output "========================================"
Write-Output "✅ 所有服务已启动！"
Write-Output "========================================"
Write-Output "🔌 MCP 服务：http://localhost:8127 (端口 8127)"
Write-Output "📢 前端访问地址：http://localhost:3000"
Write-Output "🔧 后端 API 地址：http://localhost:8123/api"
Write-Output "🎙️ 语音识别服务：ws://127.0.0.1:10095"
Write-Output ""
Write-Output "提示："
Write-Output "  • 如需停止服务，请关闭对应的 PowerShell 窗口或使用 taskkill 命令。"
Write-Output "  • 日志文件位置："
Write-Output "    - MCP: G:\IdeaProjects\lianba-ai-agent\tmp\mcp-server.log"
Write-Output "    - 后端：G:\IdeaProjects\lianba-ai-agent\tmp\backend-ds3.log"
Write-Output "    - 语音：PowerShell 窗口实时输出（隐藏模式）"
