"""快速验证 faster-whisper WS 服务协议（模拟前端 useVoiceInput.js 的通信方式）"""
import asyncio
import json

import numpy as np
import websockets


async def test():
    print("连接 ws://127.0.0.1:10095 ...")
    async with websockets.connect("ws://127.0.0.1:10095") as ws:
        # 1. 发送起始配置（前端 FunASR 协议消息，服务端应忽略）
        await ws.send(json.dumps({"mode": "2pass", "chunk_size": [5, 10, 5], "is_speaking": True}))
        print("已发送配置消息")
        # 2. 发送 1 秒 440Hz 正弦波 PCM（16kHz 16bit）
        t = np.arange(16000) / 16000.0
        pcm = (np.sin(2 * np.pi * 440 * t) * 0.3 * 32767).astype(np.int16)
        await ws.send(pcm.tobytes())
        print("已发送 1 秒 PCM 音频")
        # 3. 停止说话
        await ws.send(json.dumps({"is_speaking": False}))
        print("已发送停止信号，等待识别结果（首次运行需下载模型，可能较慢）...")
        resp = await asyncio.wait_for(ws.recv(), timeout=600)
        print("RESP:", resp)
        data = json.loads(resp)
        assert data.get("mode") == "2pass-offline", "协议不匹配"
        assert "text" in data, "缺少 text 字段"
        print("协议验证通过 ✓")


asyncio.run(test())
