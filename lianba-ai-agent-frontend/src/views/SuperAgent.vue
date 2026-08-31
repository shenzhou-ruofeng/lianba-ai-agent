<template>
  <div class="super-agent-container">
    <AppHeader />
    <div class="workspace">
      <!-- 会话侧边栏 -->
      <button class="sidebar-toggle" aria-label="打开会话列表" @click="sidebarOpen = true">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="18" x2="21" y2="18" />
        </svg>
      </button>
      <SessionSidebar
        agent-type="super"
        :mobile-open="sidebarOpen"
        @select="handleSelectSession"
        @create="handleNewSession"
        @delete="handleSessionDeleted"
      />
      <!-- 移动端遮罩 -->
      <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false"></div>

      <main class="chat-area">
        <ChatRoom
          :key="chatId"
          :messages="messages"
          :connection-status="connectionStatus"
          :status-text="currentStatusText"
          ai-type="super"
          @send-message="sendMessage"
          @stop-generation="stopGeneration"
        />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppHeader from '../components/AppHeader.vue'
import ChatRoom from '../components/ChatRoom.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { chatWithManus, chatWithManusVision, stopManusTask } from '../api'
import { useAuth } from '../composables/useAuth'
import { useSessionStore } from '../composables/useSessionStore'

// 设置页面标题和元数据
useHead({
  title: 'AI超级智能体 - 恋吧AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI超级智能体是恋吧AI超级智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    },
    {
      name: 'keywords',
      content: 'AI超级智能体,智能助手,专业问答,AI问答,专业建议,恋吧,AI智能体'
    }
  ]
})

const router = useRouter()
const { refreshLoginUser } = useAuth()
const {
  sessions,
  initSessions,
  createSession,
  selectSession,
  getSession,
  appendMessage
} = useSessionStore()

const messages = ref([])
const chatId = ref('')
const connectionStatus = ref('disconnected')
const currentStatusText = ref('')
const sidebarOpen = ref(false)
let eventSource = null
// 当前推理任务 ID（后端下发 task_id 事件后赋值，用于手动停止）
let currentTaskId = null
// 停止后的兜底定时器：若后端未及时发 stopped/[DONE]，前端主动关闭连接
let stopFallbackTimer = null

// 添加消息到列表（同时持久化到当前会话，供历史恢复与会话导出）
const addMessage = (content, isUser, type = '', extra = {}) => {
  const msg = {
    content,
    isUser,
    type,
    time: new Date().getTime(),
    ...extra
  }
  messages.value.push(msg)
  if (chatId.value) {
    appendMessage(chatId.value, msg)
  }
}

// 添加工具步骤消息
const addToolStepMessage = (step, toolCalls) => {
  // 从数组末尾向前查找同一步骤号的最新消息（避免跨请求覆盖历史记录）
  const existingIndex = messages.value.findLastIndex(m => m.type === 'tool_step' && m.step === step)
  if (existingIndex >= 0) {
    messages.value[existingIndex].toolCalls = [...toolCalls]
  } else {
    addMessage('', false, 'tool_step', { step, toolCalls: [...toolCalls] })
  }
}

// 添加/更新思考过程消息（DeepSeek 风格，可折叠展示）
const addThinkMessage = (step, content) => {
  const existingIndex = messages.value.findLastIndex(m => m.type === 'thinking' && m.step === step)
  if (existingIndex >= 0) {
    messages.value[existingIndex].content = content
  } else {
    addMessage(content, false, 'thinking', { step })
  }
}

// 切换会话：恢复该会话的消息（chatId 作为会话标识随消息一起持久化）
const handleSelectSession = (id) => {
  const session = getSession(id)
  if (!session) return
  closeEventSource()
  chatId.value = session.id
  messages.value = JSON.parse(JSON.stringify(session.messages))
  sidebarOpen.value = false
}

// 新建会话
const handleNewSession = () => {
  closeEventSource()
  const session = createSession('super')
  chatId.value = session.id
  messages.value = []
  addMessage('你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？', false, 'hint')
  sidebarOpen.value = false
}

// 删除会话后：若当前会话被删，自动进入下一个会话或新建
const handleSessionDeleted = (deletedId) => {
  if (chatId.value === deletedId) {
    const session = getSession(chatId.value)
    if (session) {
      handleSelectSession(session.id)
    } else {
      handleNewSession()
    }
  }
}

// 关闭当前 SSE 连接并重置状态
const closeEventSource = () => {
  if (stopFallbackTimer) {
    clearTimeout(stopFallbackTimer)
    stopFallbackTimer = null
  }
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  currentTaskId = null
  connectionStatus.value = 'disconnected'
  currentStatusText.value = ''
}

// 发送消息（支持图片/文档附件，附件已在待上传区解析完成）
const sendMessage = async (message, attachments = {}) => {
  const images = attachments.images || []
  const docs = attachments.docs || []

  // 引用模式：附件与提示词合并为一条用户消息（图片/文档在上，提示词在下），避免图片重复发送
  if (images.length > 0 || docs.length > 0) {
    addMessage(message, true, 'user-question', {
      quote: {
        images: images.map(img => ({
          url: img.url,
          previewUrl: img.previewUrl,
          fileName: img.fileName
        })),
        docs: docs.map(doc => ({
          fileName: doc.fileName,
          chunks: doc.chunks,
          imported: doc.imported
        }))
      }
    })
  } else if (message.trim()) {
    addMessage(message, true, 'user-question')
  } else {
    return
  }

  // 关闭之前的连接
  closeEventSource()

  // 设置连接状态
  connectionStatus.value = 'connecting'
  currentStatusText.value = '正在连接...'

  // 临时存储
  let messageBuffer = []; // 用于存储SSE消息的缓冲区
  let lastBubbleTime = Date.now(); // 上一个气泡的创建时间
  let isFirstResponse = true; // 是否是第一次响应

  const chineseEndPunctuation = ['。', '！', '？', '…']; // 中文句子结束标点
  const minBubbleInterval = 800; // 气泡最小间隔时间(毫秒)

  // 创建消息气泡的函数
  const createBubble = (content, type = 'ai-answer') => {
    if (!content.trim()) return;

    // 添加适当的延迟，使消息显示更自然
    const now = Date.now();
    const timeSinceLastBubble = now - lastBubbleTime;

    if (isFirstResponse) {
      // 第一条消息立即显示
      addMessage(content, false, type);
      isFirstResponse = false;
    } else if (timeSinceLastBubble < minBubbleInterval) {
      // 如果与上一气泡间隔太短，添加一个延迟
      setTimeout(() => {
        addMessage(content, false, type);
      }, minBubbleInterval - timeSinceLastBubble);
    } else {
      // 正常添加消息
      addMessage(content, false, type);
    }

    lastBubbleTime = now;
    messageBuffer = []; // 清空缓冲区
  };

  // 用于追踪工具步骤的临时状态
  let currentStep = 0
  let currentToolCalls = []

  // 解析 SSE JSON 消息
  const handleSseMessage = (rawData) => {
    // 尝试解析 JSON
    let parsed
    try {
      if (rawData.startsWith('{')) {
        parsed = JSON.parse(rawData)
      }
    } catch (e) {
      // 不是 JSON，按普通文本处理
    }

    if (parsed && parsed.type) {
      if (parsed.type === 'task_id') {
        // 记录任务 ID，用于后续手动停止
        currentTaskId = parsed.taskId || null
      } else if (parsed.type === 'tool_call') {
        // 新步骤开始或同步骤新工具
        if (parsed.step !== currentStep) {
          // 新步骤：先保存上一步的工具调用
          if (currentToolCalls.length > 0) {
            addToolStepMessage(currentStep, currentToolCalls)
          }
          currentStep = parsed.step
          currentToolCalls = []
        }
        currentToolCalls.push({
          toolName: parsed.toolName || '未知工具',
          arguments: parsed.arguments || '',
          result: null
        })
      } else if (parsed.type === 'tool_result') {
        // 更新最后一个工具调用的结果
        if (currentToolCalls.length > 0) {
          const lastCall = currentToolCalls[currentToolCalls.length - 1]
          if (lastCall && !lastCall.result) {
            lastCall.result = parsed.result || ''
          }
        }
        // 工具结果到达后立即展示
        if (currentToolCalls.length > 0) {
          addToolStepMessage(currentStep, currentToolCalls)
        }
      } else if (parsed.type === 'think') {
        // AI 思考过程：先保存之前的工具步骤
        if (currentToolCalls.length > 0) {
          addToolStepMessage(currentStep, currentToolCalls)
          currentToolCalls = []
        }
        const content = parsed.result || parsed.content || ''
        if (content.trim()) {
          addThinkMessage(parsed.step, content)
        }
      } else if (parsed.type === 'summary') {
        // 最终总结：先保存工具步骤与缓冲区
        if (currentToolCalls.length > 0) {
          addToolStepMessage(currentStep, currentToolCalls)
          currentToolCalls = []
        }
        if (messageBuffer.length > 0) {
          createBubble(messageBuffer.join(''))
        }
        const content = parsed.result || parsed.content || ''
        if (content.trim()) {
          addMessage(content, false, 'ai-summary')
        }
      } else if (parsed.type === 'files') {
        // 生成的可下载文件列表
        if (parsed.files && parsed.files.length > 0) {
          addMessage('', false, 'file-list', { files: parsed.files })
        }
      } else if (parsed.type === 'status') {
        // 实时状态更新（正在思考/正在执行工具等）
        // stopped 状态：后端已中断，展示停止提示卡片
        if (parsed.status === 'stopped') {
          // 后端已响应停止，取消前端兜底定时器，避免重复提示
          if (stopFallbackTimer) {
            clearTimeout(stopFallbackTimer)
            stopFallbackTimer = null
          }
          if (currentToolCalls.length > 0) {
            addToolStepMessage(currentStep, currentToolCalls)
            currentToolCalls = []
          }
          if (messageBuffer.length > 0) {
            createBubble(messageBuffer.join(''))
          }
          addMessage(parsed.message || '回复已被手动停止', false, 'ai-stopped')
          currentStatusText.value = ''
        } else {
          currentStatusText.value = parsed.message || ''
        }
      } else if (parsed.type === 'ask_human') {
        // AI 向用户提问（askHuman 交互式执行）：先保存已有工具步骤，再展示提问卡片
        if (currentToolCalls.length > 0) {
          addToolStepMessage(currentStep, currentToolCalls)
        }
        currentStatusText.value = '等待你的回复...'
        addMessage(parsed.question || 'AI 需要你提供更多信息', false, 'ask_human', {
          interactionId: parsed.interactionId,
          answered: false,
          answer: ''
        })
      } else if (parsed.type === 'generated_image') {
        // AI 生成的图片，直接渲染预览 + 下载按钮
        currentStatusText.value = '' // 清除状态提示
        if (parsed.url) {
          addMessage('', false, 'generated_image', { imageUrl: parsed.url })
        }
      } else if (parsed.type === 'text') {
        // 先保存之前的工具步骤
        if (currentToolCalls.length > 0) {
          addToolStepMessage(currentStep, currentToolCalls)
          currentToolCalls = []
        }
        // 普通文本消息，进入气泡缓冲
        const content = parsed.result || parsed.content || ''
        if (content) {
          messageBuffer.push(content)
          const combinedText = messageBuffer.join('')
          const lastChar = content.charAt(content.length - 1)
          const hasCompleteSentence = chineseEndPunctuation.includes(lastChar) || content.includes('\n\n')
          const isLongEnough = combinedText.length > 40
          if (hasCompleteSentence || isLongEnough) {
            createBubble(combinedText)
          }
        }
      }
    } else {
      // 非 JSON 消息，按普通文本处理（兼容旧格式）
      messageBuffer.push(rawData)
      const combinedText = messageBuffer.join('')
      const lastChar = rawData.charAt(rawData.length - 1)
      const hasCompleteSentence = chineseEndPunctuation.includes(lastChar) || rawData.includes('\n\n')
      const isLongEnough = combinedText.length > 40
      if (hasCompleteSentence || isLongEnough) {
        createBubble(combinedText)
      }
    }
  }

  // 绑定 SSE 事件处理器
  const bindSseEvents = (es) => {
    es.onmessage = (event) => {
      const data = event.data
      if (data && data !== '[DONE]') {
        handleSseMessage(data)
      }

      if (data === '[DONE]') {
        // 收到结束标记，清理停止兜底定时器
        if (stopFallbackTimer) {
          clearTimeout(stopFallbackTimer)
          stopFallbackTimer = null
        }
        // 保存最后的工具步骤
        if (currentToolCalls.length > 0) {
          addToolStepMessage(currentStep, currentToolCalls)
        }
        // 如果还有未显示的内容，创建最后一个气泡
        if (messageBuffer.length > 0) {
          const remainingContent = messageBuffer.join('')
          createBubble(remainingContent, 'ai-final')
        }
        // 完成后关闭连接
        currentStatusText.value = ''
        connectionStatus.value = 'disconnected'
        es.close()
      }
    }

    es.onerror = (error) => {
      console.error('SSE Error:', error)
      currentStatusText.value = ''
      connectionStatus.value = 'error'
      es.close()

      // 如果出错时有未显示的内容，也创建气泡
      if (messageBuffer.length > 0) {
        const remainingContent = messageBuffer.join('');
        createBubble(remainingContent, 'ai-error');
      }
    }
  }

  // 图片已在选择时解析完成（含上传保存与视觉理解），无需再次上传
  const uploadedImageUrls = images.map(img => img.url).filter(u => u)
  const imageUnderstandings = images.map(img => img.understanding).filter(u => u)

  // 文档附件：把文档上下文追加到发给后端的消息中，保持前端展示的提示词不变
  let outboundMessage = message.trim()
  if (docs.length > 0) {
    const docNames = docs.map(d => `"${d.fileName}"`).join('、')
    // 将文档的解析文本内联到消息中，确保 AI 可以直接读取文档内容
    const docTexts = docs
      .filter(d => d.textContent)
      .map(d => `[文档：${d.fileName}]\n${d.textContent}\n[文档结束]`)
      .join('\n\n')
    const docNote = docs.some(d => d.imported)
      ? `（我已上传文档 ${docNames} 并导入知识库，你可以在知识库中检索文档内容。**注意**：文档没有可下载的 URL 地址，不要尝试通过 downloadResource 或 scrapeWebPage 等工具去访问文件名，否则会报错。请直接根据下方的文档内容或知识库检索结果回答。）`
      : `（我已上传文档 ${docNames}，未导入知识库。**注意**：不要尝试通过 downloadResource 等工具下载该文档，请直接根据下方的文档内容回答。）`
    const baseMsg = outboundMessage || '请结合文档回答'
    outboundMessage = docTexts
      ? `${baseMsg}\n${docNote}\n\n---\n以下为上传文档的完整内容：\n\n${docTexts}`
      : `${baseMsg}\n${docNote}`
  }
  if (!outboundMessage && uploadedImageUrls.length > 0) {
    outboundMessage = '请分析我上传的图片'
  }

  if (uploadedImageUrls.length > 0) {
    // 使用视觉理解端点，携带预解析结果，后端不再重复解析
    eventSource = chatWithManusVision(
      outboundMessage,
      uploadedImageUrls,
      imageUnderstandings,
      (data) => {
        if (data && data !== '[DONE]') {
          handleSseMessage(data)
        }
        if (data === '[DONE]') {
          if (stopFallbackTimer) {
            clearTimeout(stopFallbackTimer)
            stopFallbackTimer = null
          }
          if (currentToolCalls.length > 0) {
            addToolStepMessage(currentStep, currentToolCalls)
          }
          if (messageBuffer.length > 0) {
            createBubble(messageBuffer.join(''), 'ai-final')
          }
          currentStatusText.value = ''
          connectionStatus.value = 'disconnected'
          if (eventSource) eventSource.close()
        }
      },
      (error) => {
        console.error('Vision SSE Error:', error)
        currentStatusText.value = ''
        connectionStatus.value = 'error'
        if (eventSource) eventSource.close()
        if (messageBuffer.length > 0) {
          createBubble(messageBuffer.join(''), 'ai-error')
        }
      }
    )
  } else {
    // 无图片，使用原有端点
    eventSource = chatWithManus(outboundMessage)
    bindSseEvents(eventSource)
  }
}

// 手动停止 AI 回复：通知后端中断推理，并做前端兜底关闭
const stopGeneration = async () => {
  currentStatusText.value = '正在停止...'
  // 兜底：若 2.5s 内后端未下发 stopped/[DONE]，前端主动关闭连接
  if (stopFallbackTimer) clearTimeout(stopFallbackTimer)
  stopFallbackTimer = setTimeout(() => {
    if (eventSource) eventSource.close()
    connectionStatus.value = 'disconnected'
    currentStatusText.value = ''
    addMessage('回复已被手动停止', false, 'ai-stopped')
  }, 2500)
  try {
    if (currentTaskId) {
      await stopManusTask(currentTaskId)
    } else {
      // 未拿到任务 ID，直接关闭连接
      clearTimeout(stopFallbackTimer)
      stopFallbackTimer = null
      if (eventSource) eventSource.close()
      connectionStatus.value = 'disconnected'
      currentStatusText.value = ''
      addMessage('回复已被手动停止', false, 'ai-stopped')
    }
  } catch (e) {
    console.error('停止任务失败:', e)
  }
}

// 页面加载：绑定用户会话
onMounted(async () => {
  const user = await refreshLoginUser(true)
  initSessions(user ? user.id : '')

  // 优先恢复最近的 super 会话，否则新建
  const superSessions = sessions.value.filter(s => s.agentType === 'super')
  if (superSessions.length > 0) {
    selectSession(superSessions[0].id)
    handleSelectSession(superSessions[0].id)
  } else {
    handleNewSession()
  }
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  closeEventSource()
})
</script>

<style scoped>
.super-agent-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background-color: var(--paper);
}

.workspace {
  display: flex;
  flex: 1;
  min-height: 0;
  position: relative;
}

.chat-area {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
}

/* 移动端：会话列表开关 */
.sidebar-toggle {
  display: none;
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 90;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  border: 1px solid var(--line);
  background: var(--white);
  color: var(--ink-soft);
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-soft);
}

.sidebar-mask {
  display: none;
  position: fixed;
  inset: 0;
  z-index: 110;
  background: rgba(38, 34, 28, 0.4);
  backdrop-filter: blur(2px);
}

@media (max-width: 860px) {
  .sidebar-toggle {
    display: flex;
  }

  .sidebar-mask {
    display: block;
  }
}
</style>
