"""
faster-whisper 本地语音识别 WebSocket 服务
==========================================
协议兼容前端 useVoiceInput.js 的 FunASR 2pass 简化版：
1. 客户端连接后先发送 JSON 配置消息（如 {"mode":"2pass","is_speaking":true}，服务端忽略）
2. 客户端持续发送 16kHz 16bit Int16 PCM 二进制音频
3. 客户端停止说话时发送 {"is_speaking": false}
4. 服务端对整段音频做一次识别，返回 {"mode":"2pass-offline","text":"识别结果","is_final":true}
   —— 前端 handleAsrMessage 识别到 offline 消息后自动作为定稿文本回填输入框

本地开发启动：
    pip install -r requirements.txt
    python server.py                  # 默认 ws://127.0.0.1:10095

服务器部署启动（与本地一致，仅改环境变量）：
    WHISPER_HOST=0.0.0.0 python server.py

环境变量：
    WHISPER_MODEL  模型名称（默认 small；可选 base 更快 / medium 更准 / large-v3 最佳）
    WHISPER_HOST   监听地址（默认 127.0.0.1；服务器部署设 0.0.0.0）
    WHISPER_PORT   监听端口（默认 10095，与前端 FunASR 默认地址一致）
    HF_ENDPOINT    国内网络建议 https://hf-mirror.com 加速模型下载
"""

import asyncio
import json
import logging
import os

import numpy as np
import websockets

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("whisper-ws")

MODEL_NAME = os.environ.get("WHISPER_MODEL", "small")
HOST = os.environ.get("WHISPER_HOST", "127.0.0.1")
PORT = int(os.environ.get("WHISPER_PORT", "10095"))

# 延迟加载模型（首次运行会自动下载模型文件，之后从本地缓存加载）
_model = None


def get_model():
    global _model
    if _model is None:
        log.info("加载模型 %s（首次运行会自动下载，约 500MB，请耐心等待）...", MODEL_NAME)
        from faster_whisper import WhisperModel

        _model = WhisperModel(MODEL_NAME, device="cpu", compute_type="int8")
        log.info("模型加载完成")
    return _model


async def handle_client(ws):
    pcm_chunks = []
    try:
        async for message in ws:
            if isinstance(message, (bytes, bytearray)):
                pcm_chunks.append(bytes(message))
            else:
                try:
                    data = json.loads(message)
                except json.JSONDecodeError:
                    continue
                # 客户端停止说话：结束接收音频，开始识别
                if data.get("is_speaking") is False:
                    break
    except Exception:
        pass  # 客户端异常断开，仍尝试识别已收到的音频

    if not pcm_chunks:
        return

    # 拼接 PCM 并转为 float32（[-1, 1]）
    raw = b"".join(pcm_chunks)
    audio = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    if len(audio) < 1600:  # 少于 0.1 秒的音频直接忽略
        return

    log.info("收到音频 %.1f 秒，开始识别...", len(audio) / 16000.0)
    try:
        segments, _ = get_model().transcribe(
            audio,
            language="zh",
            beam_size=1,
            vad_filter=True,
            vad_parameters=dict(min_silence_duration_ms=500),
        )
        text = "".join(seg.text for seg in segments).replace(" ", "")
        log.info("识别结果: %s", text)
        result = json.dumps({"mode": "2pass-offline", "text": text, "is_final": True}, ensure_ascii=False)
        try:
            await ws.send(result)
        except Exception:
            pass  # 客户端已断开（如等待超时），识别结果丢弃
    except Exception as e:
        log.error("识别失败: %s", e)


async def main():
    log.info("faster-whisper WebSocket 服务启动: ws://%s:%d (模型: %s)", HOST, PORT, MODEL_NAME)
    # max_size 调大以容纳整段录音的 PCM 数据
    async with websockets.serve(handle_client, HOST, PORT, max_size=64 * 1024 * 1024):
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
