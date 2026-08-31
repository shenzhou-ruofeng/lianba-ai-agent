<template>
  <div class="love-master-container">
    <AppHeader />

    <!-- Onboarding 情感状态选择遮罩 -->
    <div v-if="showOnboarding" class="onboarding-overlay">
      <div class="onboarding-card">
        <h2 class="onboarding-title">欢迎来到 AI 恋爱大师 ♡</h2>
        <p class="onboarding-desc">请选择你当前的情感状态，我会为你提供更贴心的服务</p>
        <div class="onboarding-cards">
          <button
            v-for="opt in relationshipOptions"
            :key="opt.value"
            class="onboarding-option"
            :class="{ selected: selectedStatus === opt.value }"
            @click="selectRelationshipStatus(opt.value)"
            :disabled="savingStatus"
          >
            <span class="option-emoji">{{ opt.emoji }}</span>
            <span class="option-label">{{ opt.label }}</span>
            <span class="option-desc">{{ opt.desc }}</span>
          </button>
        </div>
        <p v-if="savingStatus" class="onboarding-saving">保存中...</p>
      </div>
    </div>

    <div class="workspace">
      <!-- 会话侧边栏 -->
      <button class="sidebar-toggle" aria-label="打开会话列表" @click="sidebarOpen = true">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="18" x2="21" y2="18" />
        </svg>
      </button>
      <SessionSidebar
        agent-type="love"
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
          :report-loading="reportLoading"
          :chat-mode="chatMode"
          :match-gender="matchGender"
          :show-capability-guide="showCapabilityGuide"
          ai-type="love"
          @send-message="sendMessage"
          @stop-generation="stopGeneration"
          @generate-report="handleGenerateReport"
          @update:chat-mode="handleChatModeChange"
          @update:match-gender="handleMatchGenderChange"
        />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppHeader from '../components/AppHeader.vue'
import ChatRoom from '../components/ChatRoom.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { chatWithLoveApp, chatWithLoveAppVision, matchWithLoveApp, generateLoveReport, updateUserProfile } from '../api'
import { useAuth } from '../composables/useAuth'
import { useSessionStore } from '../composables/useSessionStore'

// 设置页面标题和元数据
useHead({
  title: 'AI恋爱大师 - 恋吧AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI恋爱大师是恋吧AI超级智能体应用平台的专业情感顾问，帮你解答各种恋爱问题，提供情感建议'
    },
    {
      name: 'keywords',
      content: 'AI恋爱大师,情感顾问,恋爱咨询,AI聊天,情感问题,恋吧,AI智能体'
    }
  ]
})

const router = useRouter()
const { refreshLoginUser, loginUser } = useAuth()
const {
  sessions,
  initSessions,
  createSession,
  selectSession,
  getSession,
  appendMessage,
  patchMessageAt,
  extractExportableMessages
} = useSessionStore()

const messages = ref([])
const chatId = ref('')
const connectionStatus = ref('disconnected')
const reportLoading = ref(false)
// 聊天模式：'chat' 恋爱咨询 / 'match' 对象推荐
const chatMode = ref('chat')
// 对象推荐期望性别：'' 不限 / '男' / '女'，后端据此硬过滤候选人
const matchGender = ref('')
const sidebarOpen = ref(false)
let eventSource = null
// 工具步骤跟踪（融合超级智能体工具后，恋爱大师也展示工具调用过程）
let currentToolStep = 0
let currentToolCalls = []

// Onboarding 情感状态选择
const showOnboarding = ref(false)
const selectedStatus = ref('')
const savingStatus = ref(false)
// 能力引导气泡：首次发送消息后展示（localStorage 持久化）
const showCapabilityGuide = ref(false)
const relationshipOptions = [
  { value: 'single', emoji: '🌸', label: '单身', desc: '想拓展社交圈、追求心仪的人' },
  { value: 'dating', emoji: '💕', label: '恋爱中', desc: '想改善沟通、解决恋爱中的小矛盾' },
  { value: 'married', emoji: '💍', label: '已婚', desc: '想处理好家庭关系、保鲜婚姻生活' }
]

// 选择情感状态后保存并关闭 Onboarding
const selectRelationshipStatus = async (status) => {
  selectedStatus.value = status
  savingStatus.value = true
  try {
    const res = await updateUserProfile(status)
    if (res.code === 0 && res.data) {
      // 同步全局登录态
      Object.assign(loginUser.value, res.data)
      showOnboarding.value = false
      // 根据选择触发个性化开场白
      const welcomeMap = {
        single: '嗨，欢迎来到 AI 恋爱大师！🌸 我是你的情感伙伴，无论你是想拓展社交圈、还是正在为追求某个人而犯愁，都可以和我聊聊。你现在有喜欢的人吗？或者有什么社交方面的困扰？',
        dating: '嗨，欢迎来到 AI 恋爱大师！💕 我是你的情感伙伴，恋爱中的甜蜜和烦恼都可以和我分享。你们最近遇到了什么困惑或矛盾吗？',
        married: '嗨，欢迎来到 AI 恋爱大师！💍 我是你的情感伙伴，婚姻生活中的柴米油盐和人际关系都可以聊聊。最近有什么让你烦恼的事情吗？'
      }
      // 替换默认的欢迎消息
      if (messages.value.length > 0 && messages.value[0].type === 'hint') {
        messages.value[0].content = welcomeMap[status] || welcomeMap.single
      }
    }
  } catch (e) {
    console.error('保存情感状态失败:', e)
  } finally {
    savingStatus.value = false
  }
}

// 添加消息到列表（同时持久化到当前会话，供历史恢复与会话导出）
// 返回消息在会话存储中的索引，供流式输出精确更新
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
    return appendMessage(chatId.value, msg)
  }
  return -1
}

// 切换会话：恢复该会话的消息、模式与后端记忆 chatId
const handleSelectSession = (id) => {
  const session = getSession(id)
  if (!session) return
  closeEventSource()
  chatId.value = session.id
  chatMode.value = session.chatMode || 'chat'
  matchGender.value = session.matchGender || ''
  messages.value = JSON.parse(JSON.stringify(session.messages))
  sidebarOpen.value = false
}

// 新建会话：生成新 chatId，重置对话
const handleNewSession = () => {
  closeEventSource()
  const session = createSession('love')
  chatId.value = session.id
  chatMode.value = 'chat'
  matchGender.value = ''
  messages.value = []
  addMessage('欢迎来到AI恋爱大师，请告诉我你的恋爱问题，我会尽力给予帮助和建议。', false, 'hint')
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

const closeEventSource = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  currentToolStep = 0
  currentToolCalls = []
  connectionStatus.value = 'disconnected'
}

// 添加工具步骤消息（可折叠展示工具调用过程）
const addToolStepMessage = (step, toolCalls) => {
  const existingIndex = messages.value.findLastIndex(m => m.type === 'tool_step' && m.step === step)
  if (existingIndex >= 0) {
    messages.value[existingIndex].toolCalls = [...toolCalls]
  } else {
    addMessage('', false, 'tool_step', { step, toolCalls: [...toolCalls] })
  }
}

// 发送消息（支持图片/文档附件，附件已在待上传区解析完成）
const sendMessage = (message, attachments = {}) => {
  const images = attachments.images || []
  const docs = attachments.docs || []

  // 引用模式：附件与提示词合并为一条用户消息
  if (images.length > 0 || docs.length > 0) {
    addMessage(message, true, 'user-question', {
      quote: {
        images: images.map(img => ({ url: img.url, previewUrl: img.previewUrl, fileName: img.fileName })),
        docs: docs.map(doc => ({ fileName: doc.fileName, chunks: doc.chunks, imported: doc.imported }))
      }
    })
  } else if (message.trim()) {
    addMessage(message, true, 'user-question')
  } else {
    return
  }

  // 构建发给后端的消息：附加图片理解结果与文档说明（前端展示保持提示词不变）
  let outboundMessage = message.trim() || '请结合我发送的内容给出建议'
  const understandings = images.map(img => img.understanding).filter(u => u)
  if (understandings.length > 0) {
    outboundMessage += '\n[图片内容描述]\n' + understandings.join('\n\n')
  }
  if (docs.length > 0) {
    const docNames = docs.map(d => `"${d.fileName}"`).join('、')
    const docTexts = docs
      .filter(d => d.textContent)
      .map(d => `[文档：${d.fileName}]\n${d.textContent}\n[文档结束]`)
      .join('\n\n')
    outboundMessage += docs.some(d => d.imported)
      ? `\n（我已上传文档 ${docNames} 并导入知识库，请在知识库中检索文档内容后回答。**注意**：不要尝试通过工具下载或访问文档文件名。）`
      : `\n（我已上传文档 ${docNames}，未导入知识库。请根据下方的文档内容回答。）`
    if (docTexts) {
      outboundMessage += `\n\n---\n以下为上传文档的完整内容：\n\n${docTexts}`
    }
  }

  // 持久化用户消息到会话（addMessage 已入库，这里只需记录展示索引）
  const aiMessageIndex = messages.value.length
  // 创建空的AI回复消息并入库，返回其在会话存储中的精确索引
  const aiSessionIndex = addMessage('', false, 'ai-answer')

  // 首次发送消息后展示能力引导气泡（仅展示一次）
  if (!showCapabilityGuide.value && !localStorage.getItem('love_guide_shown')) {
    showCapabilityGuide.value = true
    localStorage.setItem('love_guide_shown', '1')
  }

  connectionStatus.value = 'connecting'
  // 重置工具步骤跟踪
  currentToolStep = 0
  currentToolCalls = []

  // 按模式分流：对象推荐走候选人知识库推荐接口（纯文本 SSE），其他走工具调用接口（JSON SSE）
  if (chatMode.value === 'match') {
    // === 对象推荐模式：纯文本流式（保持原有逻辑） ===
    eventSource = matchWithLoveApp(outboundMessage, chatId.value, matchGender.value)
    eventSource.onmessage = (event) => {
      const data = event.data
      if (data && data !== '[DONE]') {
        if (aiMessageIndex < messages.value.length) {
          messages.value[aiMessageIndex].content += data
          patchMessageAt(chatId.value, aiSessionIndex, { content: messages.value[aiMessageIndex].content })
        }
      }
      if (data === '[DONE]') {
        connectionStatus.value = 'disconnected'
        eventSource.close()
      }
    }
    eventSource.onerror = (error) => {
      if (eventSource.readyState === EventSource.CLOSED || connectionStatus.value === 'disconnected') {
        connectionStatus.value = 'disconnected'
        eventSource.close()
        return
      }
      console.error('SSE Error:', error)
      connectionStatus.value = 'error'
      eventSource.close()
    }
  } else if (images.length > 0) {
    // === 带图片的工具调用模式：POST + JSON SSE ===
    const uploadedImageUrls = images.map(img => img.url).filter(u => u)
    eventSource = chatWithLoveAppVision(
      outboundMessage, chatId.value, uploadedImageUrls,
      (data) => handleToolsSseMessage(data, aiMessageIndex, aiSessionIndex),
      (error) => {
        console.error('Vision SSE Error:', error)
        connectionStatus.value = 'disconnected'
        if (eventSource) eventSource.close()
      }
    )
  } else {
    // === 纯文本工具调用模式：GET + JSON SSE ===
    eventSource = chatWithLoveApp(outboundMessage, chatId.value)
    eventSource.onmessage = (event) => {
      handleToolsSseMessage(event.data, aiMessageIndex, aiSessionIndex)
      if (event.data === '[DONE]') {
        connectionStatus.value = 'disconnected'
        eventSource.close()
      }
    }
    eventSource.onerror = (error) => {
      if (eventSource.readyState === EventSource.CLOSED || connectionStatus.value === 'disconnected') {
        connectionStatus.value = 'disconnected'
        eventSource.close()
        return
      }
      console.error('SSE Error:', error)
      connectionStatus.value = 'error'
      eventSource.close()
    }
  }
}

/**
 * 处理工具调用 SSE 的 JSON 消息（复用超级智能体的消息格式）
 * 支持：tool_call / tool_result / text / generated_image / files / status
 */
const handleToolsSseMessage = (rawData, aiMessageIndex, aiSessionIndex) => {
  if (!rawData || rawData === '[DONE]') return

  let parsed = null
  try {
    if (rawData.startsWith('{')) parsed = JSON.parse(rawData)
  } catch (e) { /* 不是 JSON，忽略 */ }

  if (!parsed || !parsed.type) return

  if (parsed.type === 'tool_call') {
    if (parsed.step !== currentToolStep) {
      if (currentToolCalls.length > 0) addToolStepMessage(currentToolStep, currentToolCalls)
      currentToolStep = parsed.step
      currentToolCalls = []
    }
    currentToolCalls.push({ toolName: parsed.toolName || '未知工具', arguments: parsed.arguments || '', result: null })
  } else if (parsed.type === 'tool_result') {
    if (currentToolCalls.length > 0) {
      const lastCall = currentToolCalls[currentToolCalls.length - 1]
      if (lastCall && !lastCall.result) lastCall.result = parsed.result || ''
      addToolStepMessage(currentToolStep, currentToolCalls)
    }
  } else if (parsed.type === 'text') {
    // 保存之前的工具步骤
    if (currentToolCalls.length > 0) {
      addToolStepMessage(currentToolStep, currentToolCalls)
      currentToolCalls = []
    }
    const content = parsed.content || ''
    if (content && aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].content += content
      patchMessageAt(chatId.value, aiSessionIndex, { content: messages.value[aiMessageIndex].content })
    }
  } else if (parsed.type === 'generated_image') {
    if (currentToolCalls.length > 0) {
      addToolStepMessage(currentToolStep, currentToolCalls)
      currentToolCalls = []
    }
    if (parsed.url) addMessage('', false, 'generated_image', { imageUrl: parsed.url })
  } else if (parsed.type === 'files') {
    if (currentToolCalls.length > 0) {
      addToolStepMessage(currentToolStep, currentToolCalls)
      currentToolCalls = []
    }
    if (parsed.files && parsed.files.length > 0) addMessage('', false, 'file-list', { files: parsed.files })
  } else if (parsed.type === 'status') {
    // 状态更新（正在思考/正在执行工具等），暂不展示
  }
}

// 手动停止 AI 回复：恋爱大师为普通流式对话，直接关闭 SSE 连接即可
const stopGeneration = () => {
  closeEventSource()
  addMessage('回复已被手动停止', false, 'ai-stopped')
}

// 生成恋爱报告（结构化输出，基于当前会话的历史对话）
const handleGenerateReport = async () => {
  if (reportLoading.value || connectionStatus.value === 'connecting') return
  reportLoading.value = true
  try {
    const report = await generateLoveReport('请根据我们的对话生成恋爱报告', chatId.value)
    if (report && report.title) {
      const reportMsg = {
        type: 'love_report',
        isUser: false,
        report,
        time: new Date().getTime()
      }
      messages.value.push(reportMsg)
      appendMessage(chatId.value, reportMsg)
    } else {
      addMessage('抱歉，恋爱报告生成失败，请多聊几轮后再试。', false)
    }
  } catch (e) {
    console.error('生成恋爱报告失败:', e)
    addMessage('抱歉，恋爱报告生成失败，请稍后重试。', false)
  } finally {
    reportLoading.value = false
  }
}

// 切换聊天模式，切到对象推荐时插入一条本地提示消息（不调后端）
const handleChatModeChange = (mode) => {
  chatMode.value = mode
  if (mode === 'match') {
    addMessage('已切换到对象推荐模式，和我说说你的基本情况和择偶要求吧～比如年龄、城市、喜欢的性格或职业。还可以在输入框上方选择只看男生或女生哦。', false, 'hint')
  } else {
    addMessage('已切回恋爱咨询模式，继续和我聊聊你的情感困惑吧。', false, 'hint')
  }
}

// 切换对象推荐期望性别（硬约束，作用于后续推荐请求的检索过滤）
const handleMatchGenderChange = (gender) => {
  matchGender.value = gender
}

// 持久化模式切换（模式消息不入库，仅保存模式状态）
watch(chatMode, (mode) => {
  if (chatId.value) {
    const session = getSession(chatId.value)
    if (session) session.chatMode = mode
  }
})

watch(matchGender, (gender) => {
  if (chatId.value) {
    const session = getSession(chatId.value)
    if (session) session.matchGender = gender
  }
})

// 页面加载：绑定用户会话
onMounted(async () => {
  const user = await refreshLoginUser(true)
  initSessions(user ? user.id : '')

  // 优先恢复最近的 love 会话，否则新建
  const loveSessions = sessions.value.filter(s => s.agentType === 'love')
  if (loveSessions.length > 0) {
    selectSession(loveSessions[0].id)
    handleSelectSession(loveSessions[0].id)
  } else {
    handleNewSession()
  }

  // 新用户未选择情感状态时展示 Onboarding
  if (user && !user.relationshipStatus) {
    showOnboarding.value = true
  }
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  closeEventSource()
})
</script>

<style scoped>
.love-master-container {
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

/* Onboarding 情感状态选择遮罩 */
.onboarding-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(38, 34, 28, 0.55);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 0.3s ease;
}

.onboarding-card {
  background: #fff;
  border-radius: 20px;
  padding: 40px 36px 32px;
  max-width: 560px;
  width: 90%;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.18);
  text-align: center;
}

.onboarding-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #e05575;
  margin-bottom: 8px;
}

.onboarding-desc {
  font-size: 0.92rem;
  color: #888;
  margin-bottom: 28px;
}

.onboarding-cards {
  display: flex;
  gap: 14px;
  justify-content: center;
}

.onboarding-option {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px 12px;
  border: 2px solid #f0e4e8;
  border-radius: 16px;
  background: #fff;
  cursor: pointer;
  transition: all 0.25s;
}

.onboarding-option:hover:not(:disabled) {
  border-color: #ff8fab;
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(255, 107, 139, 0.15);
}

.onboarding-option.selected {
  border-color: #ff6b8b;
  background: linear-gradient(135deg, #fff5f7, #fff);
  box-shadow: 0 8px 24px rgba(255, 107, 139, 0.2);
}

.onboarding-option:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.option-emoji {
  font-size: 2rem;
}

.option-label {
  font-size: 1rem;
  font-weight: 600;
  color: #333;
}

.option-desc {
  font-size: 0.78rem;
  color: #999;
  line-height: 1.3;
}

.onboarding-saving {
  margin-top: 16px;
  font-size: 0.85rem;
  color: #aaa;
}

@media (max-width: 480px) {
  .onboarding-card {
    padding: 28px 18px 24px;
  }
  .onboarding-cards {
    flex-direction: column;
    gap: 10px;
  }
  .onboarding-option {
    flex-direction: row;
    padding: 14px 16px;
    gap: 12px;
  }
  .option-emoji {
    font-size: 1.5rem;
  }
  .option-desc {
    display: none;
  }
}
</style>
