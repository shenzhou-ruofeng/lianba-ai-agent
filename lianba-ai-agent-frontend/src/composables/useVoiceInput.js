import { ref } from 'vue'

/**
 * 流式语音输入 composable
 *
 * 双引擎方案：
 * 1. 优先连接本地 FunASR 流式服务（Paraformer 流式模型，2pass 模式：online 实时出字 + offline 纠错）
 * 2. FunASR 不可用（未启动/连接失败）时，自动降级为浏览器原生 Web Speech API（SpeechRecognition，zh-CN），
 *    保证语音输入功能在任何环境都可用
 *
 * FunASR 部署参考（官方 runtime websocket 服务，默认端口 10095）：
 *   https://github.com/modelscope/FunASR/blob/main/runtime/docs/SDK_advanced_guide_online_zh.md
 * 可通过环境变量 VITE_FUNASR_WS_URL 覆盖服务地址。
 */
export function useVoiceInput() {
  const wsUrl = import.meta.env.VITE_FUNASR_WS_URL || 'ws://127.0.0.1:10095'

  // 识别引擎：'funasr'（本地服务）/ 'web'（浏览器降级）/ ''
  let engine = ''
  // 是否正在录音
  const isRecording = ref(false)
  // 是否正在建立连接
  const isConnecting = ref(false)
  // 错误/降级提示
  const voiceError = ref('')

  let ws = null
  let audioContext = null
  let mediaStream = null
  let sourceNode = null
  let processorNode = null
  // Web Speech API 识别器
  let recognition = null
  let webRecognitionStopped = false
  // 2pass 识别文本：offline 已定稿文本 + online 实时临时文本
  let finalText = ''
  let partialText = ''
  // 识别文本变化回调
  let textChangeCallback = null
  // 停止后等待 offline 最终结果的定时器
  let closeTimer = null

  // 将浏览器采样率的 Float32 音频重采样为 16kHz Int16 PCM
  const downsampleToPcm16k = (input, sampleRate) => {
    const targetRate = 16000
    if (sampleRate === targetRate) {
      const pcm = new Int16Array(input.length)
      for (let i = 0; i < input.length; i++) {
        const s = Math.max(-1, Math.min(1, input[i]))
        pcm[i] = s < 0 ? s * 0x8000 : s * 0x7fff
      }
      return pcm
    }
    const ratio = sampleRate / targetRate
    const newLength = Math.floor(input.length / ratio)
    const pcm = new Int16Array(newLength)
    for (let i = 0; i < newLength; i++) {
      const s = Math.max(-1, Math.min(1, input[Math.floor(i * ratio)]))
      pcm[i] = s < 0 ? s * 0x8000 : s * 0x7fff
    }
    return pcm
  }

  // 通知外部当前识别文本（已定稿 + 实时临时）
  const emitText = () => {
    if (textChangeCallback) {
      textChangeCallback(finalText + partialText)
    }
  }

  // 处理 FunASR 返回的识别结果
  const handleAsrMessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      const text = (msg.text || '').replace(/ +/g, '')
      const mode = msg.mode || ''
      if (!text && !msg.is_final) return
      if (mode.indexOf('offline') >= 0) {
        // offline（二遍）结果：替换本句的 online 临时文本，作为定稿
        finalText += text
        partialText = ''
      } else {
        // online（流式）结果：累加为临时文本
        partialText += text
      }
      emitText()
    } catch (e) {
      // 忽略非 JSON 消息
    }
  }

  // 处理 Web Speech API 的识别结果
  const handleWebSpeechResult = (event) => {
    let interim = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const transcript = event.results[i][0].transcript
      if (event.results[i].isFinal) {
        finalText += transcript
      } else {
        interim += transcript
      }
    }
    partialText = interim
    emitText()
  }

  // 释放音频采集资源
  const releaseAudio = () => {
    if (processorNode) {
      try { processorNode.disconnect() } catch (e) { /* ignore */ }
      processorNode.onaudioprocess = null
      processorNode = null
    }
    if (sourceNode) {
      try { sourceNode.disconnect() } catch (e) { /* ignore */ }
      sourceNode = null
    }
    if (audioContext) {
      try { audioContext.close() } catch (e) { /* ignore */ }
      audioContext = null
    }
    if (mediaStream) {
      mediaStream.getTracks().forEach(track => track.stop())
      mediaStream = null
    }
  }

  // 释放 Web Speech API 识别器
  const releaseWebRecognition = () => {
    if (recognition) {
      recognition.onresult = null
      recognition.onerror = null
      recognition.onend = null
      try { recognition.stop() } catch (e) { /* ignore */ }
      recognition = null
    }
  }

  // 关闭 WebSocket
  const closeWs = () => {
    if (closeTimer) {
      clearTimeout(closeTimer)
      closeTimer = null
    }
    if (ws) {
      try { ws.close() } catch (e) { /* ignore */ }
      ws = null
    }
  }

  // 降级启动浏览器原生语音识别（zh-CN）
  const startWebRecognition = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SpeechRecognition) {
      voiceError.value = '本地 FunASR 服务未启动，且当前浏览器不支持语音识别降级方案'
      return false
    }
    engine = 'web'
    webRecognitionStopped = false
    recognition = new SpeechRecognition()
    recognition.lang = 'zh-CN'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.maxAlternatives = 1
    recognition.onresult = handleWebSpeechResult
    recognition.onerror = (e) => {
      if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
        voiceError.value = '浏览器拒绝了麦克风权限，无法使用语音输入'
      } else if (e.error === 'no-speech') {
        // 无语音输入，静默处理
      } else if (!webRecognitionStopped) {
        voiceError.value = `浏览器语音识别异常（${e.error}）`
      }
    }
    recognition.onend = () => {
      // 非手动停止时自动继续监听（continuous 模式下部分浏览器仍会触发 onend）
      if (!webRecognitionStopped && isRecording.value) {
        try { recognition?.start() } catch (e) { /* ignore */ }
      }
    }
    try {
      recognition.start()
      return true
    } catch (e) {
      voiceError.value = '浏览器语音识别启动失败'
      return false
    }
  }

  /**
   * 开始录音识别
   * @param {Function} onTextChange 识别文本变化回调，参数为当前完整识别文本
   */
  const startVoiceInput = async (onTextChange) => {
    if (isRecording.value || isConnecting.value) return
    voiceError.value = ''
    finalText = ''
    partialText = ''
    textChangeCallback = onTextChange
    isConnecting.value = true

    try {
      // 1. 申请麦克风权限
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (e) {
      isConnecting.value = false
      voiceError.value = '无法访问麦克风，请检查浏览器权限设置'
      return
    }

    // 2. 优先连接本地 FunASR 流式服务
    try {
      await new Promise((resolve, reject) => {
        ws = new WebSocket(wsUrl)
        ws.binaryType = 'arraybuffer'
        const connectTimeout = setTimeout(() => reject(new Error('连接超时')), 3000)
        ws.onopen = () => {
          clearTimeout(connectTimeout)
          resolve()
        }
        ws.onerror = () => {
          clearTimeout(connectTimeout)
          reject(new Error('连接失败'))
        }
      })
      engine = 'funasr'
    } catch (e) {
      // FunASR 不可用：释放连接，尝试浏览器 Web Speech API 降级
      closeWs()
      const webOk = startWebRecognition()
      if (webOk) {
        // 浏览器降级方案使用同一路麦克风流（Web Speech API 内部会自行采集，此处先释放自己采集的流）
        releaseAudio()
        voiceError.value = '本地 FunASR 服务未启动，已自动降级为浏览器语音识别（效果略逊于 FunASR）'
      } else {
        releaseAudio()
        closeWs()
        isConnecting.value = false
        voiceError.value = `无法连接语音识别服务（${wsUrl}），请确认本地 FunASR 服务已启动`
        return
      }
    }

    // FunASR 模式：继续走 WebSocket 流式协议
    if (engine === 'funasr') {
      ws.onmessage = handleAsrMessage
      ws.onerror = () => {
        voiceError.value = '语音识别服务连接异常'
        stopVoiceInput()
      }

      // 3. 发送 FunASR 流式协议的起始配置（2pass：online 实时出字 + offline 纠错）
      ws.send(JSON.stringify({
        mode: '2pass',
        chunk_size: [5, 10, 5],
        chunk_interval: 10,
        wav_name: 'web-voice-input',
        is_speaking: true,
        hotwords: '',
        itn: true
      }))

      // 4. 采集麦克风音频并分片发送
      audioContext = new (window.AudioContext || window.webkitAudioContext)()
      sourceNode = audioContext.createMediaStreamSource(mediaStream)
      processorNode = audioContext.createScriptProcessor(4096, 1, 1)
      processorNode.onaudioprocess = (e) => {
        if (!ws || ws.readyState !== WebSocket.OPEN) return
        const input = e.inputBuffer.getChannelData(0)
        const pcm = downsampleToPcm16k(input, audioContext.sampleRate)
        ws.send(pcm.buffer)
      }
      sourceNode.connect(processorNode)
      processorNode.connect(audioContext.destination)
    }

    isConnecting.value = false
    isRecording.value = true
  }

  /**
   * 停止录音：FunASR 通知服务端语音结束等待最终纠错；Web Speech 直接停止监听
   */
  const stopVoiceInput = () => {
    if (!isRecording.value && !isConnecting.value) return
    isRecording.value = false
    isConnecting.value = false

    if (engine === 'funasr') {
      releaseAudio()
      if (ws && ws.readyState === WebSocket.OPEN) {
        try {
          ws.send(JSON.stringify({ is_speaking: false }))
        } catch (e) { /* ignore */ }
        // 等待 offline 二遍识别的最终结果返回后再关闭
        // 8 秒窗口：兼容本地 faster-whisper 整段识别（CPU 上耗时较长），FunASR 结果通常 1 秒内返回不受影响
        closeTimer = setTimeout(() => {
          closeWs()
          textChangeCallback = null
        }, 8000)
      } else {
        closeWs()
        textChangeCallback = null
      }
    } else if (engine === 'web') {
      webRecognitionStopped = true
      releaseWebRecognition()
      // 浏览器引擎为一次性监听，直接结束
      textChangeCallback = null
    }
    engine = ''
  }

  return {
    isRecording,
    isConnecting,
    voiceError,
    startVoiceInput,
    stopVoiceInput
  }
}
