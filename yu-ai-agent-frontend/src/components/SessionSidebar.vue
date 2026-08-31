<template>
  <aside class="session-sidebar" :class="{ 'is-mobile-open': mobileOpen }">
    <!-- 侧边栏头部：新建会话 -->
    <div class="sidebar-head">
      <button class="new-session-btn" @click="handleCreate">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        新建会话
      </button>
    </div>

    <!-- 会话列表 -->
    <div class="session-list" role="list" aria-label="历史会话列表">
      <div v-if="filteredSessions.length === 0" class="session-empty">
        <span class="empty-icon" aria-hidden="true">✧</span>
        <p>还没有历史会话</p>
        <p class="empty-hint">点击上方「新建会话」开始对话</p>
      </div>
      <button
        v-for="session in filteredSessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === activeSessionId }"
        role="listitem"
        @click="handleSelect(session.id)"
      >
        <span class="session-dot" :class="{ love: session.agentType === 'love' }" aria-hidden="true"></span>
        <span class="session-main">
          <span class="session-title">{{ session.title }}</span>
          <span class="session-meta">{{ formatTime(session.updatedAt) }} · {{ session.messages.length }} 条消息</span>
        </span>
        <span class="session-delete" role="button" aria-label="删除会话" @click.stop="handleDelete(session.id)">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          </svg>
        </span>
      </button>
    </div>

    <!-- 底部：导出入口 -->
    <div class="sidebar-foot">
      <router-link to="/export" class="export-link">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
        </svg>
        导出会话信息
      </router-link>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../composables/useSessionStore'

const props = defineProps({
  agentType: {
    type: String,
    required: true
  },
  mobileOpen: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'create', 'delete'])

const router = useRouter()
const { sessions, activeSessionId, selectSession, deleteSession } = useSessionStore()

const filteredSessions = computed(() =>
  sessions.value.filter(s => s.agentType === props.agentType)
)

const formatTime = (ts) => {
  if (!ts) return ''
  const date = new Date(ts)
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  const pad = n => String(n).padStart(2, '0')
  if (sameDay) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return `${date.getMonth() + 1}/${date.getDate()}`
}

const handleCreate = () => {
  emit('create')
}

const handleSelect = (id) => {
  selectSession(id)
  emit('select', id)
}

const handleDelete = (id) => {
  const session = sessions.value.find(s => s.id === id)
  const label = session ? session.title : '该会话'
  if (window.confirm(`确定删除「${label}」吗？删除后无法恢复。`)) {
    deleteSession(id)
    emit('delete', id)
  }
}
</script>

<style scoped>
.session-sidebar {
  width: 264px;
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.72);
  border-right: 1px solid var(--line);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.sidebar-head {
  padding: 16px 14px 10px;
}

.new-session-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 14px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--grad-rose);
  color: var(--white);
  font-size: 0.92rem;
  font-weight: 600;
  box-shadow: var(--shadow-rose);
  transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast);
}

.new-session-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 36px rgba(224, 90, 114, 0.32);
}

.new-session-btn:active {
  transform: translateY(0);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-empty {
  text-align: center;
  padding: 48px 16px;
  color: var(--ink-faint);
}

.empty-icon {
  font-size: 1.8rem;
  display: block;
  margin-bottom: 10px;
  color: var(--amber);
}

.empty-hint {
  font-size: 0.8rem;
  margin-top: 6px;
  color: var(--ink-faint);
  opacity: 0.8;
}

.session-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 11px 12px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  text-align: left;
  transition: background var(--dur-fast) var(--ease-out);
}

.session-item:hover {
  background: rgba(224, 90, 114, 0.06);
}

.session-item.active {
  background: rgba(224, 90, 114, 0.1);
  box-shadow: inset 3px 0 0 var(--rose);
}

.session-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sage);
  margin-top: 6px;
  flex-shrink: 0;
}

.session-dot.love {
  background: var(--rose);
}

.session-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.session-title {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.active .session-title {
  color: var(--rose-deep);
}

.session-meta {
  font-size: 0.72rem;
  color: var(--ink-faint);
}

.session-delete {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 26px;
  height: 26px;
  display: none;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: var(--ink-faint);
  background: var(--white);
  border: 1px solid var(--line);
  transition: color var(--dur-fast), border-color var(--dur-fast);
}

.session-item:hover .session-delete {
  display: flex;
}

.session-delete:hover {
  color: var(--rose-deep);
  border-color: rgba(224, 90, 114, 0.4);
}

.sidebar-foot {
  padding: 12px 14px 16px;
  border-top: 1px solid var(--line);
}

.export-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--ink-soft);
  border: 1px dashed var(--line);
  transition: color var(--dur-fast), border-color var(--dur-fast), background var(--dur-fast);
}

.export-link:hover {
  color: var(--rose-deep);
  border-color: rgba(224, 90, 114, 0.45);
  background: rgba(224, 90, 114, 0.04);
}

/* 移动端：抽屉式 */
@media (max-width: 860px) {
  .session-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 120;
    width: 280px;
    transform: translateX(-105%);
    transition: transform var(--dur-med) var(--ease-out);
    background: var(--paper);
    box-shadow: var(--shadow-float);
  }

  .session-sidebar.is-mobile-open {
    transform: translateX(0);
  }
}
</style>
