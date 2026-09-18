# 启动 FunASR/faster-whisper 本地语音识别 WebSocket 服务
# 依赖：pip install faster-whisper websockets numpy
# 环境变量：WHISPER_MODEL (默认 small), WHISPER_PORT (默认 10095), HF_ENDPOINT (可选，国内建议 https://hf-mirror.com)

$env:HF_ENDPOINT = "https://hf-mirror.com"
$env:PYTHONUNBUFFERED = "1"

Write-Output "正在启动 faster-whisper 语音识别服务..."
python.exe server.py
