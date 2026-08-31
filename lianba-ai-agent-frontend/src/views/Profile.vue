<template>
  <div class="profile-page">
    <AppHeader />

    <main class="profile-main">
      <div class="profile-card">
        <div class="profile-side">
          <div class="avatar-large">{{ avatarText }}</div>
          <h1 class="display-name">{{ displayName }}</h1>
          <p class="account">{{ loginUser?.userAccount }}</p>
          <span class="role-badge" :class="isAdmin ? 'role-admin' : 'role-user'">
            {{ isAdmin ? '管理员' : '普通用户' }}
          </span>
        </div>

        <div class="profile-body">
          <!-- 基本信息 -->
          <section class="info-section">
            <h2 class="section-title">基本信息</h2>
            <div class="info-rows">
              <div class="info-row">
                <span class="info-label">用户 ID</span>
                <span class="info-value">{{ loginUser?.id || '—' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">账号</span>
                <span class="info-value">{{ loginUser?.userAccount || '—' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">角色</span>
                <span class="info-value">{{ isAdmin ? '管理员' : '普通用户' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">注册时间</span>
                <span class="info-value">{{ formatDate(loginUser?.createTime) }}</span>
              </div>
            </div>
          </section>

          <!-- 编辑昵称 -->
          <section class="info-section">
            <h2 class="section-title">编辑个人信息</h2>
            <div class="nickname-edit">
              <div class="edit-field">
                <label class="edit-label" for="nickname">昵称</label>
                <div class="edit-row">
                  <input
                    id="nickname"
                    v-model.trim="nickname"
                    class="edit-input"
                    type="text"
                    maxlength="20"
                    placeholder="给自己起个好听的名字吧"
                  />
                  <button class="save-btn" :disabled="saving || !nickname" @click="saveNickname">
                    {{ saving ? '保存中...' : '保存昵称' }}
                  </button>
                </div>
                <p class="edit-hint" :class="savedTip === '昵称已保存 ✧' ? 'hint-success' : savedTip ? 'hint-error' : ''">
                  {{ savedTip || '昵称将显示在网站右上角与个人资料中' }}
                </p>
              </div>
            </div>
          </section>

          <!-- 会话统计 -->
          <section class="info-section">
            <h2 class="section-title">会话概览</h2>
            <div class="stat-grid">
              <div class="stat-box">
                <span class="stat-box-num">{{ loveCount }}</span>
                <span class="stat-box-label">恋爱大师会话</span>
              </div>
              <div class="stat-box">
                <span class="stat-box-num">{{ superCount }}</span>
                <span class="stat-box-label">超级智能体会话</span>
              </div>
              <div class="stat-box">
                <span class="stat-box-num">{{ totalMessages }}</span>
                <span class="stat-box-label">对话消息</span>
              </div>
            </div>
          </section>

          <!-- 快捷操作 -->
          <section class="info-section">
            <h2 class="section-title">快捷操作</h2>
            <div class="quick-actions">
              <router-link to="/export" class="quick-btn">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
                </svg>
                导出会话信息
              </router-link>
              <router-link to="/love-master" class="quick-btn">
                <span aria-hidden="true">♡</span>
                去和恋爱大师聊聊
              </router-link>
              <router-link to="/super-agent" class="quick-btn">
                <span aria-hidden="true">✦</span>
                去和超级智能体聊聊
              </router-link>
            </div>
          </section>
        </div>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useHead } from '@vueuse/head'
import AppHeader from '../components/AppHeader.vue'
import AppFooter from '../components/AppFooter.vue'
import { useAuth } from '../composables/useAuth'
import { useSessionStore } from '../composables/useSessionStore'

useHead({
  title: '个人信息 - 恋吧AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: '查看和编辑你的恋吧AI超级智能体应用平台个人信息，管理账号与会话数据。'
    }
  ]
})

const { loginUser, refreshLoginUser, updateNickname } = useAuth()
const { sessions, initSessions } = useSessionStore()

// 昵称编辑：保存到后端（同时同步 Session 登录态，右上角昵称立即生效）
const nickname = ref('')
const saving = ref(false)
const savedTip = ref('')

const displayName = computed(() => loginUser.value?.userName || loginUser.value?.userAccount || '用户')
const avatarText = computed(() => (displayName.value || '?').charAt(0).toUpperCase())
const isAdmin = computed(() => loginUser.value?.userRole === 'admin')

const loveCount = computed(() => sessions.value.filter(s => s.agentType === 'love').length)
const superCount = computed(() => sessions.value.filter(s => s.agentType === 'super').length)
const totalMessages = computed(() => sessions.value.reduce((sum, s) => sum + s.messages.length, 0))

const formatDate = (ts) => {
  if (!ts) return '—'
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const saveNickname = async () => {
  if (!nickname.value) return
  saving.value = true
  savedTip.value = ''
  try {
    await updateNickname(nickname.value)
    savedTip.value = '昵称已保存 ✧'
  } catch (e) {
    console.error('保存昵称失败:', e)
    savedTip.value = '保存失败，请稍后重试'
  } finally {
    saving.value = false
    setTimeout(() => { savedTip.value = '' }, 2500)
  }
}

onMounted(async () => {
  const user = await refreshLoginUser(true)
  initSessions(user ? user.id : '')
  // 预填当前昵称供编辑
  nickname.value = user?.userName || ''
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--paper);
}

.profile-main {
  flex: 1;
  max-width: 1000px;
  width: 100%;
  margin: 0 auto;
  padding: 64px 28px 100px;
}

.profile-card {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 40px;
  background: var(--white);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: 48px 44px;
  position: relative;
  overflow: hidden;
}

.profile-card::before {
  content: '';
  position: absolute;
  top: -100px;
  right: -100px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(224, 90, 114, 0.1), transparent 65%);
  pointer-events: none;
}

.profile-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding-top: 12px;
}

.avatar-large {
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: var(--grad-rose);
  color: var(--white);
  font-family: var(--font-display);
  font-size: 2.6rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-rose);
  margin-bottom: 22px;
}

.display-name {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 6px;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account {
  font-size: 0.88rem;
  color: var(--ink-faint);
  margin-bottom: 14px;
}

.role-badge {
  font-size: 0.76rem;
  font-weight: 600;
  padding: 4px 14px;
  border-radius: 999px;
}

.role-user {
  background: rgba(46, 125, 116, 0.1);
  color: var(--sage);
}

.role-admin {
  background: rgba(224, 90, 114, 0.1);
  color: var(--rose-deep);
}

.profile-body {
  display: flex;
  flex-direction: column;
  gap: 36px;
  min-width: 0;
}

.info-section {
  border-bottom: 1px dashed var(--line);
  padding-bottom: 30px;
}

.info-section:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.section-title {
  font-family: var(--font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 18px;
}

.info-rows {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 11px 0;
  border-bottom: 1px solid rgba(232, 224, 212, 0.6);
  font-size: 0.92rem;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  width: 110px;
  color: var(--ink-faint);
  flex-shrink: 0;
}

.info-value {
  color: var(--ink);
  font-weight: 500;
}

/* 昵称编辑 */
.edit-field {
  max-width: 460px;
}

.edit-label {
  display: block;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--ink-soft);
  margin-bottom: 10px;
}

.edit-row {
  display: flex;
  gap: 12px;
}

.edit-input {
  flex: 1;
  padding: 12px 16px;
  border: 1.5px solid var(--line);
  border-radius: var(--radius-sm);
  font-size: 0.95rem;
  color: var(--ink);
  background: var(--paper);
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast);
}

.edit-input:focus {
  outline: none;
  border-color: var(--rose);
  box-shadow: 0 0 0 3px rgba(224, 90, 114, 0.1);
  background: var(--white);
}

.save-btn {
  padding: 12px 24px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--grad-rose);
  color: var(--white);
  font-size: 0.9rem;
  font-weight: 600;
  box-shadow: var(--shadow-rose);
  transition: transform var(--dur-fast), box-shadow var(--dur-fast), opacity var(--dur-fast);
  flex-shrink: 0;
}

.save-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 14px 32px rgba(224, 90, 114, 0.3);
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.edit-hint {
  margin-top: 10px;
  font-size: 0.78rem;
  color: var(--ink-faint);
  min-height: 1.2em;
}

.edit-hint.hint-success {
  color: var(--sage);
  font-weight: 600;
}

.edit-hint.hint-error {
  color: var(--rose-deep);
  font-weight: 600;
}

/* 会话统计 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.stat-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px 12px;
  border-radius: var(--radius-md);
  background: var(--grad-soft);
  border: 1px solid var(--line);
}

.stat-box-num {
  font-family: var(--font-display);
  font-size: 1.7rem;
  font-weight: 700;
  background: var(--grad-rose);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.stat-box-label {
  font-size: 0.78rem;
  color: var(--ink-soft);
}

/* 快捷操作 */
.quick-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.quick-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 20px;
  border-radius: 999px;
  border: 1.5px solid var(--line);
  background: var(--white);
  font-size: 0.88rem;
  font-weight: 500;
  color: var(--ink-soft);
  transition: all var(--dur-fast);
}

.quick-btn:hover {
  border-color: var(--rose);
  color: var(--rose-deep);
  transform: translateY(-2px);
  box-shadow: var(--shadow-soft);
}

/* 响应式 */
@media (max-width: 820px) {
  .profile-card {
    grid-template-columns: 1fr;
    padding: 36px 26px;
    gap: 28px;
  }

  .profile-side {
    padding-top: 0;
  }
}

@media (max-width: 520px) {
  .profile-main {
    padding: 40px 16px 70px;
  }

  .edit-row {
    flex-direction: column;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
