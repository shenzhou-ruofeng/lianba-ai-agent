import { ref } from 'vue'

/**
 * 会话管理 store（模块级单例）
 *
 * 后端聊天记忆按 chatId 持久化（Kryo 文件），但没有会话列表接口，
 * 因此会话索引由前端 localStorage 维护（按用户隔离），
 * 每个会话的 id 即后端记忆使用的 chatId —— 选择历史会话后继续对话，
 * 后端会自动延续该会话的上下文，与现有聊天记录系统完全兼容。
 *
 * 会话结构：
 * {
 *   id: 'love_xxxx' | 'super_xxxx',   // 即后端 chatId
 *   agentType: 'love' | 'super',
 *   title: '新会话',
 *   createdAt, updatedAt: 时间戳,
 *   messages: [...],                  // 与聊天页面消息结构一致
 *   chatMode: 'chat' | 'match',       // love 专属
 *   matchGender: '' | '男' | '女'      // love 专属
 * }
 */

const STORAGE_PREFIX = 'yu_ai_chat_sessions_'

// 当前登录用户 ID（由页面在加载会话前设置）
const ownerId = ref('')
// 当前选中会话 ID
const activeSessionId = ref('')
// 当前用户的所有会话（按 agentType 过滤后渲染）
const sessions = ref([])

const storageKey = () => STORAGE_PREFIX + (ownerId.value || 'anonymous')

const persist = () => {
  try {
    localStorage.setItem(storageKey(), JSON.stringify(sessions.value))
  } catch (e) {
    // localStorage 不可用（隐私模式等）时静默降级为内存会话
  }
}

const readStorage = () => {
  try {
    const raw = localStorage.getItem(storageKey())
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    return []
  }
}

/**
 * 初始化：绑定当前用户并加载其会话列表
 */
const initSessions = (userId) => {
  if (userId && userId !== ownerId.value) {
    ownerId.value = userId
    sessions.value = readStorage()
  }
  if (!ownerId.value) {
    sessions.value = []
  }
}

/**
 * 生成会话 ID（与旧版 LoveMaster 的 chatId 生成规则一致）
 */
const generateSessionId = (agentType) => {
  const prefix = agentType === 'love' ? 'love' : 'super'
  return prefix + '_' + Math.random().toString(36).substring(2, 10)
}

/**
 * 新建会话，返回会话对象（已持久化）
 */
const createSession = (agentType) => {
  const now = Date.now()
  const session = {
    id: generateSessionId(agentType),
    agentType,
    title: '新会话',
    createdAt: now,
    updatedAt: now,
    messages: [],
    chatMode: 'chat',
    matchGender: ''
  }
  sessions.value.unshift(session)
  activeSessionId.value = session.id
  persist()
  return session
}

/**
 * 选择会话
 */
const selectSession = (id) => {
  activeSessionId.value = id
}

/**
 * 获取会话
 */
const getSession = (id) => {
  return sessions.value.find(s => s.id === id)
}

const getActiveSession = () => {
  return getSession(activeSessionId.value)
}

/**
 * 更新会话字段（title / chatMode 等）
 */
const updateSession = (id, patch) => {
  const session = getSession(id)
  if (!session) return null
  Object.assign(session, patch, { updatedAt: Date.now() })
  persist()
  return session
}

/**
 * 追加一条消息；若为会话第一条用户消息，自动截取为会话标题。
 * 返回消息在 session.messages 中的索引（供流式输出精确更新，避免定位错消息）
 */
const appendMessage = (id, message) => {
  const session = getSession(id)
  if (!session) return -1
  session.messages.push(message)
  session.updatedAt = Date.now()
  if (message.isUser && message.content && session.title === '新会话') {
    session.title = message.content.trim().replace(/\s+/g, ' ').slice(0, 18)
  }
  persist()
  return session.messages.length - 1
}

/**
 * 按索引精确更新会话中某条消息（流式输出追加内容时使用，
 * 避免因会话中存在未入库的本地消息或中途追加新消息而 patch 错用户消息）
 */
const patchMessageAt = (id, index, patch) => {
  const session = getSession(id)
  if (!session || !session.messages[index]) return
  Object.assign(session.messages[index], patch)
  session.updatedAt = Date.now()
  persist()
}

/**
 * 删除会话
 */
const deleteSession = (id) => {
  const index = sessions.value.findIndex(s => s.id === id)
  if (index >= 0) {
    sessions.value.splice(index, 1)
    if (activeSessionId.value === id) {
      activeSessionId.value = sessions.value[Math.min(index, sessions.value.length - 1)]?.id || ''
    }
    persist()
  }
}

/**
 * 导出时提取可导出的消息（过滤工具步骤/思考过程等过程性消息）
 * 返回 [{ role: 'user' | 'ai', content }]
 */
const extractExportableMessages = (session) => {
  if (!session) return []
  const result = []
  for (const msg of session.messages) {
    // 跳过过程性消息
    if (msg.type === 'tool_step' || msg.type === 'thinking' || msg.type === 'ai-stopped' || msg.type === 'hint') {
      continue
    }
    // 恋爱报告卡片：转成文字摘要
    if (msg.type === 'love_report' && msg.report) {
      const lines = [`${msg.report.title}`]
      ;(msg.report.suggestions || []).forEach((s, i) => lines.push(`${i + 1}. ${s}`))
      result.push({ role: 'ai', content: lines.join('\n') })
      continue
    }
    // 图片生成等特殊卡片：附上说明
    if (msg.type === 'generated_image') {
      result.push({ role: 'ai', content: '[AI 生成的图片]' })
      continue
    }
    if (msg.type === 'file-list' && msg.files) {
      result.push({ role: 'ai', content: '[生成的文件] ' + msg.files.map(f => f.name).join('、') })
      continue
    }
    if (msg.isUser === undefined && !msg.content) {
      continue
    }
    // 含附件引用时，拼接附件说明
    let content = msg.content || ''
    if (msg.quote) {
      const parts = []
      if (msg.quote.images?.length) {
        parts.push('[图片] ' + msg.quote.images.map(i => i.fileName).join('、'))
      }
      if (msg.quote.docs?.length) {
        parts.push('[文档] ' + msg.quote.docs.map(d => d.fileName).join('、'))
      }
      if (parts.length) {
        content = content ? `${content}\n（附：${parts.join('；')}）` : content
      }
    }
    if (content) {
      result.push({ role: msg.isUser ? 'user' : 'ai', content })
    }
  }
  return result
}

export function useSessionStore() {
  return {
    ownerId,
    sessions,
    activeSessionId,
    initSessions,
    createSession,
    selectSession,
    getSession,
    getActiveSession,
    updateSession,
    appendMessage,
    patchMessageAt,
    deleteSession,
    extractExportableMessages
  }
}
