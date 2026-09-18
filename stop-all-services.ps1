# 停止所有服务（MCP + 语音识别 + 后端 API + 前端）
# 用法：powershell -ExecutionPolicy Bypass -File stop-all-services.ps1

Write-Output "正在停止所有服务..."

# 停止 MCP 服务
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*mcp*" } | Stop-Process -Force
Write-Output "✅ MCP 服务已停止"

# 停止 faster-whisper 语音识别服务
Get-Process -Name python -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*server.py*" } | Stop-Process -Force
Write-Output "✅ 语音识别服务已停止"

# 停止 Spring Boot 后端
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*AiAgentApplication*" } | Stop-Process -Force
Write-Output "✅ 后端服务已停止"

# 停止 Vite 前端
Get-Process -Name node -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*vite*" } | Stop-Process -Force
Write-Output "✅ 前端服务已停止"

Write-Output ""
Write-Output "所有服务已停止完成！"
