# 启动 faster-whisper 语音识别服务（本地开发）
# 用法：powershell -ExecutionPolicy Bypass -File run-whisper.ps1
$env:HF_ENDPOINT = 'https://hf-mirror.com'          # 国内加速模型下载
$env:HF_HUB_DISABLE_XET = '1'                        # 禁用 Xet 下载（hf-mirror 兼容）
$env:HF_HUB_DISABLE_SYMLINKS_WARNING = '1'           # 关闭 Windows symlink 警告
Set-Location "$PSScriptRoot"
python server.py
