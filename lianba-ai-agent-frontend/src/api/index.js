import axios from 'axios'

// 根据环境变量设置 API 基础 URL
export const API_BASE_URL = process.env.NODE_ENV === 'production' 
 ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
 : 'http://localhost:8123/api' // 开发环境指向本地后端服务

// 创建axios实例（携带 Cookie，用于 Session 登录态）
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true
})

// 公开页面白名单：未登录访问这些页面时不强制跳转（是否拦截由路由守卫决定）
const PUBLIC_PATHS = ['/', '/login']

// 响应拦截器：未登录（40100）统一跳转登录页（公开页面除外）
request.interceptors.response.use(
  response => {
    const data = response.data
    if (data && data.code === 40100 && !PUBLIC_PATHS.includes(window.location.pathname)) {
      window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
    }
    return response
  },
  error => {
    if (error.response && error.response.status === 401 && !PUBLIC_PATHS.includes(window.location.pathname)) {
      window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
    }
    return Promise.reject(error)
  }
)

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  
  // 创建EventSource（携带 Cookie，后端鉴权依赖 Session）
  const eventSource = new EventSource(fullUrl, { withCredentials: true })
  
  eventSource.onmessage = event => {
    let data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }
  
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  
  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

// AI恋爱大师聊天（带工具调用能力，融合超级智能体工具）
export const chatWithLoveApp = (message, chatId) => {
  return connectSSE('/ai/love_app/chat/tools_sse', { message, chatId })
}

// AI恋爱大师聊天（带工具调用 + 图片支持，POST + JSON body + SSE）
export const chatWithLoveAppVision = (message, chatId, imageUrls = [], onMessage, onError) => {
  const url = `${API_BASE_URL}/ai/love_app/chat/tools_sse`
  const controller = new AbortController()
  const eventSourceLike = {
    close: () => controller.abort(),
    onmessage: null,
    onerror: null
  }

  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ message, chatId, imageUrls: imageUrls || [] }),
    signal: controller.signal
  }).then(async response => {
    if (!response.ok) {
      const err = new Error(`HTTP ${response.status}`)
      if (eventSourceLike.onerror) eventSourceLike.onerror(err)
      if (onError) onError(err)
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const data = trimmed.substring(5).trim()
          if (eventSourceLike.onmessage) eventSourceLike.onmessage({ data })
          if (onMessage) onMessage(data)
        }
      }
    }
    if (buffer.trim().startsWith('data:')) {
      const data = buffer.trim().substring(5).trim()
      if (eventSourceLike.onmessage) eventSourceLike.onmessage({ data })
      if (onMessage) onMessage(data)
    }
  }).catch(err => {
    if (err.name !== 'AbortError') {
      if (eventSourceLike.onerror) eventSourceLike.onerror(err)
      if (onError) onError(err)
    }
  })

  return eventSourceLike
}

// AI恋爱对象推荐（基于 RAG 候选人知识库，SSE 流式）
export const matchWithLoveApp = (message, chatId, gender) => {
  const params = { message, chatId }
  // 选择了期望性别时才传参，后端据此在检索层硬过滤候选人
  if (gender) {
    params.gender = gender
  }
  return connectSSE('/ai/love_app/match/sse', params)
}

// 生成恋爱报告（结构化输出，同步调用，返回 { title, suggestions }）
export const generateLoveReport = async (message, chatId) => {
  const res = await request.get('/ai/love_app/chat/report', {
    params: { message, chatId },
    timeout: 120000
  })
  return res.data
}

// 导出恋爱报告为文件并触发浏览器下载（format: 'pdf' | 'word' | 'md'）
export const exportLoveReport = async (report, format) => {
  const res = await request.post('/ai/love_app/report/export', report, {
    params: { format },
    responseType: 'blob',
    timeout: 60000
  })
  // 从 Content-Disposition 解析文件名，失败时用报告标题拼接兼容扩展名
  const extMap = { pdf: '.pdf', word: '.doc', md: '.md' }
  let fileName = (report.title || '恋爱报告') + (extMap[format] || '')
  const disposition = res.headers['content-disposition']
  if (disposition) {
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/)
    if (match) {
      fileName = decodeURIComponent(match[1])
    }
  }
  // 用 Blob URL 触发下载
  const blobUrl = URL.createObjectURL(res.data)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

// AI超级智能体聊天
export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

// 提交用户对智能体 askHuman 提问的回复（交互式执行）
// 后端返回统一的 BaseResponse：{ code, data, message }
export const replyToManus = async (interactionId, answer) => {
  const res = await request.post('/ai/manus/human_reply', null, {
    params: { interactionId, answer },
    timeout: 15000
  })
  return res.data
}

// AI超级智能体聊天（支持图片，POST + JSON body + SSE 流式响应）
// imageUnderstandings：前端选图时已预解析的视觉理解结果，后端优先使用，避免重复解析
// 返回一个兼容 EventSource 接口的对象（含 close 方法），用于 POST 方式接收 SSE
export const chatWithManusVision = (message, imageUrls = [], imageUnderstandings = [], onMessage, onError) => {
  const url = `${API_BASE_URL}/ai/manus/chat/vision`
  const controller = new AbortController()
  const eventSourceLike = {
    close: () => controller.abort(),
    onmessage: null,
    onerror: null
  }

  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({
      message: message,
      imageUrls: imageUrls || [],
      imageUnderstandings: imageUnderstandings || []
    }),
    signal: controller.signal
  }).then(async response => {
    if (!response.ok) {
      const err = new Error(`HTTP ${response.status}`)
      if (eventSourceLike.onerror) eventSourceLike.onerror(err)
      if (onError) onError(err)
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 按行分割 SSE 数据
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 保留未完成的行
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const data = trimmed.substring(5).trim()
          if (eventSourceLike.onmessage) {
            eventSourceLike.onmessage({ data })
          }
          if (onMessage) onMessage(data)
        }
      }
    }
    // 处理缓冲区剩余内容
    if (buffer.trim().startsWith('data:')) {
      const data = buffer.trim().substring(5).trim()
      if (eventSourceLike.onmessage) eventSourceLike.onmessage({ data })
      if (onMessage) onMessage(data)
    }
  }).catch(err => {
    if (err.name !== 'AbortError') {
      if (eventSourceLike.onerror) eventSourceLike.onerror(err)
      if (onError) onError(err)
    }
  })

  return eventSourceLike
}

// 上传图片到服务器
export const uploadImages = async (files) => {
  const formData = new FormData()
  files.forEach(file => {
    formData.append('files', file)
  })
  // 不手动设置 Content-Type，axios 会自动添加正确的 boundary 参数
  const res = await request.post('/images/upload', formData, {
    timeout: 60000
  })
  return res.data
}

// 上传文档到RAG知识库（选择即入库，旧流程兼容保留）
export const uploadRagDocument = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/rag/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

// 解析图片（上传保存 + MIMO 视觉理解一步完成，返回图片URL与理解文本）
export const parseImage = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await request.post('/images/parse', formData, {
    timeout: 120000
  })
  return res.data
}

// 解析文档（不入库，返回 parseId 待确认）
export const parseRagDocument = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await request.post('/rag/parse', formData, {
    timeout: 120000
  })
  return res.data
}

// 确认已解析的文档入库
export const confirmRagImport = async (parseId) => {
  const formData = new FormData()
  formData.append('parseId', parseId)
  const res = await request.post('/rag/confirm', formData, {
    timeout: 120000
  })
  return res.data
}

// 丢弃已解析的文档（用户移除待上传文档时调用）
export const discardRagDocument = async (parseId) => {
  const formData = new FormData()
  formData.append('parseId', parseId)
  const res = await request.post('/rag/discard', formData)
  return res.data
}

// ============ 用户登录相关 ============

// 发送邮箱注册验证码（未配置邮件服务时返回验证码，便于本地开发）
export const sendEmailCode = async (email) => {
  const res = await request.post('/user/send_code', { email })
  return res.data
}

// 用户注册（邮箱验证码校验，邮箱即登录账号）
export const userRegister = async (email, emailCode, userPassword, checkPassword) => {
  const res = await request.post('/user/register', { email, emailCode, userPassword, checkPassword })
  return res.data
}

// 用户登录
export const userLogin = async (userAccount, userPassword) => {
  const res = await request.post('/user/login', { userAccount, userPassword })
  return res.data
}

// 用户注销
export const userLogout = async () => {
  const res = await request.post('/user/logout')
  return res.data
}

// 获取当前登录用户（未登录时后端返回 40100）
export const getLoginUser = async () => {
  const res = await request.get('/user/current')
  return res.data
}

// 更新当前登录用户昵称（后端同步 Session 登录态，保存后右上角立即生效）
export const updateUserNickname = async (userName) => {
  const res = await request.post('/user/update', { userName })
  return res.data
}

// 更新用户画像（Onboarding 情感状态选择）
export const updateUserProfile = async (relationshipStatus) => {
  const res = await request.post('/user/update_profile', { relationshipStatus })
  return res.data
}

// 查询当前用户的恋爱报告列表
export const listLoveReports = async () => {
  const res = await request.get('/ai/love_app/report/list')
  return res.data
}

// 查询单个恋爱报告详情
export const getLoveReport = async (id) => {
  const res = await request.get('/ai/love_app/report/detail', { params: { id } })
  return res.data
}

// 导出通用会话记录为文件并触发浏览器下载（format: 'pdf' | 'word' | 'md'）
// messages: [{ role: 'user' | 'ai', content }]，与聊天记录系统会话数据兼容
export const exportChatSession = async (title, messages, format) => {
  const res = await request.post('/ai/export/chat', {
    title,
    messages: messages.map(m => ({ role: m.role, content: m.content }))
  }, {
    params: { format },
    responseType: 'blob',
    timeout: 60000
  })
  const extMap = { pdf: '.pdf', word: '.doc', md: '.md' }
  let fileName = (title || '对话记录') + (extMap[format] || '')
  const disposition = res.headers['content-disposition']
  if (disposition) {
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/)
    if (match) {
      fileName = decodeURIComponent(match[1])
    }
  }
  const blobUrl = URL.createObjectURL(res.data)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

// ============ 智能体任务相关 ============

// 手动停止智能体任务（中断 SSE 输出）
export const stopManusTask = async (taskId) => {
  const res = await request.post('/ai/manus/stop', null, {
    params: { taskId },
    timeout: 15000
  })
  return res.data
}

// 查询当前用户最近的智能体任务列表
export const listManusTasks = async () => {
  const res = await request.get('/ai/manus/task/list')
  return res.data
}

// 查询单个智能体任务状态
export const getManusTask = async (taskId) => {
  const res = await request.get('/ai/manus/task', { params: { taskId } })
  return res.data
}

export default {
  chatWithLoveApp,
  chatWithLoveAppVision,
  matchWithLoveApp,
  generateLoveReport,
  exportLoveReport,
  chatWithManus,
  chatWithManusVision,
  replyToManus,
  uploadImages,
  uploadRagDocument,
  parseImage,
  parseRagDocument,
  confirmRagImport,
  discardRagDocument,
  sendEmailCode,
  userRegister,
  userLogin,
  userLogout,
  getLoginUser,
  updateUserNickname,
  updateUserProfile,
  listLoveReports,
  getLoveReport,
  exportChatSession,
  stopManusTask,
  listManusTasks,
  getManusTask
} 