# faster-whisper 语音识别服务

基于 [faster-whisper](https://github.com/SYSTRAN/faster-whisper) 的本地语音识别 WebSocket 服务，协议兼容前端 `useVoiceInput.js`（FunASR 2pass 简化版），前端无需改动即可使用。

## 协议说明

1. 客户端连接 `ws://127.0.0.1:10095` 后先发送 JSON 配置消息（如 `{"mode":"2pass","is_speaking":true}`，服务端忽略内容）
2. 客户端持续发送 **16kHz / 16bit / 单声道 PCM** 二进制音频
3. 客户端停止说话时发送 `{"is_speaking": false}`
4. 服务端对整段音频做一次识别，返回 `{"mode":"2pass-offline","text":"识别结果","is_final":true}`
   —— 前端收到 `offline` 消息后自动作为定稿文本回填输入框

## 本地开发启动

```powershell
pip install -r requirements.txt
python server.py        # 默认 ws://127.0.0.1:10095，首次运行自动下载模型（约 500MB）
```

或直接运行脚本：`powershell -ExecutionPolicy Bypass -File run-whisper.ps1`

国内网络首次下载模型已内置加速配置（HF 镜像 + 禁用 Xet 下载，否则会报 401）。

## 服务器部署（项目上线时）

与本地完全一致，只需把监听地址改为 `0.0.0.0`：

```bash
# 安装依赖
pip install -r requirements.txt

# 后台启动（生产可用 systemd / supervisor 守护）
WHISPER_HOST=0.0.0.0 WHISPER_PORT=10095 nohup python server.py > whisper.log 2>&1 &

# 服务器若无法直连 HuggingFace，同样设置
# HF_ENDPOINT=https://hf-mirror.com HF_HUB_DISABLE_XET=1
```

前端连接地址通过环境变量配置（构建时生效）：`VITE_FUNASR_WS_URL=ws://服务器IP:10095`

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `WHISPER_MODEL` | `small` | 模型名：`base` 更快 / `small` 均衡 / `medium` 更准 / `large-v3` 最佳 |
| `WHISPER_HOST` | `127.0.0.1` | 监听地址，服务器部署设 `0.0.0.0` |
| `WHISPER_PORT` | `10095` | 监听端口（与前端 FunASR 默认地址一致） |
| `HF_ENDPOINT` | - | HuggingFace 镜像，国内建议 `https://hf-mirror.com` |
| `HF_HUB_DISABLE_XET` | - | 镜像下载 401 时设为 `1` 强制传统下载 |

## 测试

```bash
python test_client.py
```

发送 1 秒正弦波验证协议链路（真实语音需用浏览器语音按钮）。
