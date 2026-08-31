<template>
  <div class="export-page">
    <AppHeader />

    <main class="export-main">
      <div class="page-head">
        <p class="page-eyebrow">Session Export</p>
        <h1 class="page-title">导出会话信息</h1>
        <p class="page-desc">管理你的历史对话，选择会话导出为 PDF / Word / Markdown 文件，重要内容随时存档。</p>
      </div>

      <!-- 统计条 -->
      <div class="stats-bar">
        <div class="stat">
          <span class="stat-num">{{ totalSessions }}</span>
          <span class="stat-label">会话总数</span>
        </div>
        <div class="stat">
          <span class="stat-num">{{ totalMessages }}</span>
          <span class="stat-label">消息总数</span>
        </div>
        <div class="stat">
          <span class="stat-num">{{ formats.length }}</span>
          <span class="stat-label">支持格式</span>
        </div>
      </div>

      <!-- 筛选区 -->
      <div class="filter-bar">
        <div class="filter-tabs" role="tablist" aria-label="按智能体筛选">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            class="filter-tab"
            :class="{ active: activeTab === tab.value }"
            role="tab"
            @click="activeTab = tab.value"
          >{{ tab.label }}<span class="tab-count">{{ tab.count }}</span></button>
        </div>
        <div class="search-box">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input v-model.trim="keyword" type="search" placeholder="搜索会话标题..." aria-label="搜索会话" />
        </div>
      </div>

      <!-- 会话列表 -->
      <div v-if="filteredSessions.length === 0" class="empty-state">
        <span class="empty-icon" aria-hidden="true">❋</span>
        <p class="empty-title">{{ sessions.length === 0 ? '还没有可导出的会话' : '没有匹配的会话' }}</p>
        <p class="empty-hint">先去与智能体对话，历史会话会自动出现在这里</p>
        <div class="empty-actions">
          <router-link to="/love-master" class="cta cta-primary">去和恋爱大师聊聊</router-link>
        </div>
      </div>

      <div v-else class="session-cards">
        <article
          v-for="session in filteredSessions"
          :key="session.id"
          class="session-card"
          :class="{ selected: isSelected(session.id) }"
        >
          <label class="select-box" :title="isSelected(session.id) ? '取消选择' : '选择此会话'">
            <input
              type="checkbox"
              :checked="isSelected(session.id)"
              @change="toggleSelect(session.id)"
            />
            <span class="checkbox-ui" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true"><polyline points="20 6 9 17 4 12" /></svg>
            </span>
          </label>

          <div class="card-body">
            <div class="card-top">
              <span class="agent-tag" :class="session.agentType === 'love' ? 'tag-love' : 'tag-super'">
                {{ session.agentType === 'love' ? 'AI恋爱大师' : 'AI超级智能体' }}
              </span>
              <span class="card-time">{{ formatDate(session.updatedAt) }}</span>
            </div>
            <h3 class="card-title">{{ session.title }}</h3>
            <p class="card-preview">{{ lastMessagePreview(session) }}</p>
            <div class="card-meta">
              <span>{{ session.messages.length }} 条消息</span>
              <span class="meta-dot" aria-hidden="true">·</span>
              <span>{{ countWords(session) }} 字</span>
            </div>
          </div>

          <div class="card-actions">
            <span class="actions-label">导出为</span>
            <button
              v-for="f in formats"
              :key="f.value"
              class="format-btn"
              :class="'fmt-' + f.value"
              :disabled="exportingId === session.id"
              @click="handleExport(session, f.value)"
            >
              <svg v-if="exportingId === session.id" class="spin" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true">
                <path d="M21 12a9 9 0 1 1-6.2-8.56" />
              </svg>
              <template v-else>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
                </svg>
              </template>
              {{ f.label }}
            </button>
            <button class="delete-btn" :disabled="exportingId === session.id" title="删除会话" @click="handleDelete(session)">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              </svg>
            </button>
          </div>
        </article>
      </div>
    </main>

    <!-- 批量操作浮层 -->
    <transition name="batch-pop">
      <div v-if="selectedIds.length > 0" class="batch-bar">
        <span class="batch-count">已选 <strong>{{ selectedIds.length }}</strong> 个会话</span>
        <div class="batch-actions">
          <span class="batch-label">合并导出：</span>
          <button class="batch-btn" :disabled="batchExporting" @click="handleBatchExport('pdf')">
            <span class="fmt-dot dot-pdf"></span>PDF
          </button>
          <button class="batch-btn" :disabled="batchExporting" @click="handleBatchExport('word')">
            <span class="fmt-dot dot-word"></span>Word
          </button>
          <button class="batch-btn" :disabled="batchExporting" @click="handleBatchExport('md')">
            <span class="fmt-dot dot-md"></span>Markdown
          </button>
          <button class="batch-clear" @click="clearSelection">取消选择</button>
        </div>
      </div>
    </transition>

    <AppFooter />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useHead } from '@vueuse/head'
import AppHeader from '../components/AppHeader.vue'
import AppFooter from '../components/AppFooter.vue'
import { exportChatSession } from '../api'
import { useAuth } from '../composables/useAuth'
import { useSessionStore } from '../composables/useSessionStore'

useHead({
  title: '导出会话信息 - 恋吧AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: '导出恋吧AI超级智能体应用平台的会话信息，支持 PDF、Word、Markdown 三种格式，轻松存档你的 AI 对话记录。'
    },
    {
      name: 'keywords',
      content: '会话导出,AI对话记录,PDF导出,Word导出,Markdown导出,聊天记录备份,恋吧AI'
    }
  ]
})

const { refreshLoginUser } = useAuth()
const {
  sessions,
  initSessions,
  deleteSession,
  extractExportableMessages
} = useSessionStore()

const formats = [
  { value: 'pdf', label: 'PDF' },
  { value: 'word', label: 'Word' },
  { value: 'md', label: 'Markdown' }
]

const activeTab = ref('all')
const keyword = ref('')
const selectedIds = ref([])
const exportingId = ref('')
const batchExporting = ref(false)

const tabs = computed(() => [
  { value: 'all', label: '全部', count: sessions.value.length },
  { value: 'love', label: 'AI恋爱大师', count: sessions.value.filter(s => s.agentType === 'love').length },
  { value: 'super', label: 'AI超级智能体', count: sessions.value.filter(s => s.agentType === 'super').length }
])

const filteredSessions = computed(() => {
  let list = sessions.value
  if (activeTab.value !== 'all') {
    list = list.filter(s => s.agentType === activeTab.value)
  }
  if (keyword.value) {
    const kw = keyword.value.toLowerCase()
    list = list.filter(s => (s.title || '').toLowerCase().includes(kw))
  }
  return [...list].sort((a, b) => b.updatedAt - a.updatedAt)
})

const totalSessions = computed(() => sessions.value.length)
const totalMessages = computed(() => sessions.value.reduce((sum, s) => sum + s.messages.length, 0))

const formatDate = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const lastMessagePreview = (session) => {
  const messages = extractExportableMessages(session)
  const last = messages[messages.length - 1]
  if (!last) return '暂无对话内容'
  const role = last.role === 'user' ? '我' : 'AI'
  const text = (last.content || '').replace(/\s+/g, ' ').trim()
  return `${role}：${text.slice(0, 60)}${text.length > 60 ? '…' : ''}`
}

const countWords = (session) => {
  return extractExportableMessages(session).reduce((sum, m) => sum + (m.content || '').length, 0)
}

/**
 * 构建导出标题：会话有自定义标题（非“新会话”）时直接使用，
 * 否则用第一条用户消息自动总结会话主题，保证导出文件标题能概括对话内容
 */
const buildExportTitle = (session) => {
  if (session.title && session.title !== '新会话') {
    return session.title
  }
  const exportable = extractExportableMessages(session)
  const firstUserMsg = exportable.find(m => m.role === 'user')
  if (firstUserMsg && firstUserMsg.content) {
    return '对话：' + firstUserMsg.content.trim().replace(/\s+/g, ' ').slice(0, 24)
  }
  return '对话记录'
}

const isSelected = (id) => selectedIds.value.includes(id)

const toggleSelect = (id) => {
  if (isSelected(id)) {
    selectedIds.value = selectedIds.value.filter(sid => sid !== id)
  } else {
    selectedIds.value.push(id)
  }
}

const clearSelection = () => {
  selectedIds.value = []
}

// 单个会话导出
const handleExport = async (session, format) => {
  const exportable = extractExportableMessages(session)
  if (exportable.length === 0) {
    window.alert('该会话暂无可以导出的对话内容')
    return
  }
  exportingId.value = session.id
  try {
    await exportChatSession(buildExportTitle(session), exportable, format)
  } catch (e) {
    console.error('导出失败:', e)
    window.alert('导出失败，请稍后重试')
  } finally {
    exportingId.value = ''
  }
}

// 批量合并导出：多个会话合并为一个文档
const handleBatchExport = async (format) => {
  const sessionsToExport = sessions.value.filter(s => selectedIds.value.includes(s.id))
  if (sessionsToExport.length === 0) return
  batchExporting.value = true
  try {
    const merged = []
    for (const session of sessionsToExport) {
      const exportable = extractExportableMessages(session)
      if (exportable.length === 0) continue
      merged.push({
        role: 'ai',
        content: `——— 会话：${buildExportTitle(session)}（${formatDate(session.updatedAt)}）———`
      })
      merged.push(...exportable)
    }
    if (merged.length === 0) {
      window.alert('所选会话均无可导出的内容')
      return
    }
    await exportChatSession('恋吧AI会话导出合集', merged, format)
  } catch (e) {
    console.error('批量导出失败:', e)
    window.alert('导出失败，请稍后重试')
  } finally {
    batchExporting.value = false
  }
}

const handleDelete = (session) => {
  if (window.confirm(`确定删除「${session.title}」吗？删除后无法恢复。`)) {
    deleteSession(session.id)
    selectedIds.value = selectedIds.value.filter(id => id !== session.id)
  }
}

// 切换筛选时清空选中
watch(activeTab, clearSelection)

onMounted(async () => {
  const user = await refreshLoginUser(true)
  initSessions(user ? user.id : '')
})
</script>

<style scoped>
.export-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--paper);
}

.export-main {
  flex: 1;
  max-width: 1080px;
  width: 100%;
  margin: 0 auto;
  padding: 64px 28px 100px;
}

.page-head {
  text-align: center;
  margin-bottom: 44px;
}

.page-eyebrow {
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--amber);
  margin-bottom: 12px;
}

.page-title {
  font-family: var(--font-display);
  font-size: clamp(1.8rem, 4vw, 2.6rem);
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 14px;
}

.page-desc {
  color: var(--ink-soft);
  font-size: 1rem;
  line-height: 1.8;
  max-width: 560px;
  margin: 0 auto;
}

/* 统计条 */
.stats-bar {
  display: flex;
  justify-content: center;
  gap: 0;
  margin-bottom: 40px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--white);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.stat {
  flex: 1;
  max-width: 200px;
  padding: 22px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border-right: 1px solid var(--line);
}

.stat:last-child {
  border-right: none;
}

.stat-num {
  font-family: var(--font-display);
  font-size: 1.8rem;
  font-weight: 700;
  background: var(--grad-rose);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--ink-faint);
}

/* 筛选 */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 26px;
  flex-wrap: wrap;
}

.filter-tabs {
  display: flex;
  gap: 6px;
  background: var(--paper-deep);
  padding: 4px;
  border-radius: 14px;
}

.filter-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  border: none;
  border-radius: 11px;
  background: transparent;
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--ink-soft);
  transition: background var(--dur-fast), color var(--dur-fast), box-shadow var(--dur-fast);
}

.filter-tab.active {
  background: var(--white);
  color: var(--rose-deep);
  box-shadow: var(--shadow-soft);
}

.tab-count {
  font-size: 0.72rem;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(38, 34, 28, 0.06);
  color: var(--ink-faint);
}

.filter-tab.active .tab-count {
  background: rgba(224, 90, 114, 0.12);
  color: var(--rose-deep);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border: 1.5px solid var(--line);
  border-radius: 999px;
  background: var(--white);
  color: var(--ink-faint);
  min-width: 240px;
  transition: border-color var(--dur-fast);
}

.search-box:focus-within {
  border-color: rgba(224, 90, 114, 0.45);
}

.search-box input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 0.9rem;
  color: var(--ink);
  width: 100%;
}

/* 会话卡片 */
.session-cards {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.session-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 22px 24px;
  background: var(--white);
  border: 1.5px solid var(--line);
  border-radius: var(--radius-md);
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast), transform var(--dur-fast);
}

.session-card:hover {
  border-color: rgba(224, 90, 114, 0.3);
  box-shadow: var(--shadow-soft);
  transform: translateY(-2px);
}

.session-card.selected {
  border-color: var(--rose);
  box-shadow: 0 0 0 3px rgba(224, 90, 114, 0.12);
}

/* 复选框 */
.select-box {
  display: flex;
  align-items: center;
  padding-top: 4px;
  cursor: pointer;
}

.select-box input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.checkbox-ui {
  width: 20px;
  height: 20px;
  border-radius: 7px;
  border: 1.8px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
  color: transparent;
  transition: all var(--dur-fast);
  background: var(--white);
}

.select-box input:checked + .checkbox-ui {
  background: var(--grad-rose);
  border-color: transparent;
  color: var(--white);
}

.select-box input:focus-visible + .checkbox-ui {
  outline: 2px solid var(--rose);
  outline-offset: 2px;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.agent-tag {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  letter-spacing: 0.04em;
}

.tag-love {
  background: rgba(224, 90, 114, 0.1);
  color: var(--rose-deep);
}

.tag-super {
  background: rgba(46, 125, 116, 0.1);
  color: var(--sage);
}

.card-time {
  font-size: 0.78rem;
  color: var(--ink-faint);
}

.card-title {
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 6px;
}

.card-preview {
  font-size: 0.88rem;
  color: var(--ink-soft);
  line-height: 1.7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 560px;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 0.76rem;
  color: var(--ink-faint);
}

.meta-dot {
  color: var(--line);
}

/* 操作按钮 */
.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-top: 2px;
}

.actions-label {
  font-size: 0.76rem;
  color: var(--ink-faint);
  margin-right: 2px;
}

.format-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 13px;
  border-radius: 10px;
  border: 1.5px solid var(--line);
  background: var(--white);
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--ink-soft);
  transition: all var(--dur-fast);
}

.format-btn:hover:not(:disabled) {
  border-color: var(--rose);
  color: var(--rose-deep);
  transform: translateY(-1px);
}

.format-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.fmt-pdf:hover {
  border-color: var(--rose);
  color: var(--rose-deep);
}

.fmt-word:hover {
  border-color: var(--sage);
  color: var(--sage);
}

.fmt-md:hover {
  border-color: var(--amber);
  color: #b06a1e;
}

.spin {
  animation: rotate 0.8s linear infinite;
}

@keyframes rotate {
  to { transform: rotate(360deg); }
}

.delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1.5px solid transparent;
  background: transparent;
  color: var(--ink-faint);
  transition: all var(--dur-fast);
}

.delete-btn:hover:not(:disabled) {
  color: var(--rose-deep);
  background: rgba(224, 90, 114, 0.08);
  border-color: rgba(224, 90, 114, 0.25);
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 90px 20px;
}

.empty-icon {
  font-size: 2.6rem;
  color: var(--amber);
  display: block;
  margin-bottom: 16px;
  opacity: 0.8;
}

.empty-title {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 0.9rem;
  color: var(--ink-faint);
  margin-bottom: 30px;
}

.empty-actions {
  display: flex;
  gap: 14px;
  justify-content: center;
  flex-wrap: wrap;
}

.cta {
  display: inline-flex;
  align-items: center;
  padding: 12px 26px;
  border-radius: 999px;
  font-size: 0.92rem;
  font-weight: 600;
  transition: transform var(--dur-med) var(--ease-out), box-shadow var(--dur-med);
}

.cta:hover {
  transform: translateY(-2px);
}

.cta-primary {
  background: var(--grad-rose);
  color: var(--white);
  box-shadow: var(--shadow-rose);
}

.cta-ghost {
  background: var(--white);
  color: var(--ink);
  border: 1.5px solid var(--line);
}

.cta-ghost:hover {
  border-color: rgba(224, 90, 114, 0.4);
}

/* 批量操作浮层 */
.batch-bar {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 200;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 14px 24px;
  background: var(--ink);
  color: var(--white);
  border-radius: 18px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
  white-space: nowrap;
}

.batch-count {
  font-size: 0.9rem;
}

.batch-count strong {
  color: #ffb4c2;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-label {
  font-size: 0.82rem;
  color: rgba(255, 255, 255, 0.65);
}

.batch-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.08);
  color: var(--white);
  font-size: 0.82rem;
  font-weight: 600;
  transition: background var(--dur-fast), border-color var(--dur-fast);
}

.batch-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.4);
}

.batch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fmt-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-pdf { background: #ff8fa3; }
.dot-word { background: #7fc4bd; }
.dot-md { background: #ffc894; }

.batch-clear {
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.55);
  font-size: 0.82rem;
  padding: 8px;
  transition: color var(--dur-fast);
}

.batch-clear:hover {
  color: var(--white);
}

.batch-pop-enter-active,
.batch-pop-leave-active {
  transition: opacity var(--dur-med) var(--ease-out), transform var(--dur-med) var(--ease-out);
}

.batch-pop-enter-from,
.batch-pop-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(20px);
}

/* 响应式 */
@media (max-width: 860px) {
  .session-card {
    flex-direction: column;
    gap: 14px;
  }

  .card-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .actions-label {
    width: 100%;
  }

  .batch-bar {
    left: 16px;
    right: 16px;
    transform: none;
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .batch-pop-enter-from,
  .batch-pop-leave-to {
    transform: translateY(20px);
  }

  .batch-actions {
    flex-wrap: wrap;
  }
}

@media (max-width: 560px) {
  .export-main {
    padding: 48px 16px 80px;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-tabs {
    overflow-x: auto;
  }

  .search-box {
    min-width: 0;
  }

  .stats-bar .stat {
    padding: 16px 8px;
  }

  .stat-num {
    font-size: 1.4rem;
  }
}
</style>
