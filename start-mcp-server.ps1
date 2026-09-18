# 启动 MCP 图片搜索服务（端口 8127）
# 依赖：JDK21+
# 用法：powershell -ExecutionPolicy Bypass -File start-mcp-server.ps1

$env:JAVA_HOME = "G:\.jdks\JDK21"
$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"

Write-Output "正在启动 MCP 图片搜索服务..."
& $javaExe '-Dfile.encoding=UTF-8' -jar "G:\IdeaProjects\lianba-ai-agent\deploy\mcp-server\yu-image-search-mcp-server-0.0.1-SNAPSHOT.jar" *> "G:\IdeaProjects\lianba-ai-agent\tmp\mcp-server.log"
