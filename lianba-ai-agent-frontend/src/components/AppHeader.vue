<template>
  <header class="app-header" :class="{ 'is-scrolled': scrolled }">
    <div class="header-inner">
      <!-- Logo -->
      <router-link to="/" class="brand" aria-label="恋吧AI首页">
        <span class="brand-mark" aria-hidden="true">
          <span class="brand-mark-core"></span>
        </span>
        <span class="brand-name">恋吧<em>AI</em></span>
      </router-link>

      <!-- 主导航 -->
      <nav class="main-nav" aria-label="主导航">
        <router-link to="/" class="nav-link" :class="{ active: isHome }">首页</router-link>
        <router-link to="/love-master" class="nav-link" :class="{ active: isLove }">
          AI 恋爱大师
        </router-link>
        <router-link to="/diary" class="nav-link" :class="{ active: isDiary }">
          情感日记
        </router-link>
        <router-link to="/community" class="nav-link" :class="{ active: isCommunity }">
          情感社区
        </router-link>
        <router-link to="/reports" class="nav-link" :class="{ active: isReportHistory }">
          恋爱报告历史
        </router-link>
        <router-link to="/export" class="nav-link" :class="{ active: isExport }">
          导出会话
        </router-link>
      </nav>

      <!-- 用户区域 -->
      <div class="user-area">
        <template v-if="isLoggedIn && loginUser">
          <div class="user-menu" ref="menuRef">
            <button class="user-trigger" aria-haspopup="true" :aria-expanded="menuOpen" @click="toggleMenu">
              <span class="user-avatar">{{ avatarText }}</span>
              <span class="user-name">{{ displayName }}</span>
              <svg class="chevron" :class="{ open: menuOpen }" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </button>
            <transition name="menu-pop">
              <div v-if="menuOpen" class="dropdown" role="menu">
                <div class="dropdown-head">
                  <span class="dropdown-avatar">{{ avatarText }}</span>
                  <div class="dropdown-meta">
                    <span class="dropdown-name">{{ displayName }}</span>
                    <span class="dropdown-account">{{ loginUser.userAccount }}</span>
                  </div>
                </div>
                <div class="dropdown-divider"></div>
                <button class="dropdown-item" role="menuitem" @click="goProfile">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                  编辑个人信息
                </button>
                <button class="dropdown-item" role="menuitem" @click="goExport">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                  导出会话信息
                </button>
                <div class="dropdown-divider"></div>
                <button class="dropdown-item danger" role="menuitem" @click="doLogout">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                  退出登录
                </button>
              </div>
            </transition>
          </div>
        </template>
        <template v-else>
          <router-link to="/login" class="login-btn">登录 / 注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const route = useRoute()
const router = useRouter()
const { loginUser, isLoggedIn, refreshLoginUser, handleLogout } = useAuth()

const scrolled = ref(false)
const menuOpen = ref(false)
const menuRef = ref(null)

const displayName = computed(() => loginUser.value?.userName || loginUser.value?.userAccount || '用户')
const avatarText = computed(() => (displayName.value || '?').charAt(0).toUpperCase())

const isHome = computed(() => route.path === '/')
const isLove = computed(() => route.path.startsWith('/love-master'))
const isDiary = computed(() => route.path === '/diary')
const isCommunity = computed(() => route.path === '/community')
const isReportHistory = computed(() => route.path === '/reports')
const isExport = computed(() => route.path.startsWith('/export'))

const toggleMenu = () => {
  menuOpen.value = !menuOpen.value
}

const closeMenu = () => {
  menuOpen.value = false
}

const goProfile = () => {
  closeMenu()
  router.push('/profile')
}

const goExport = () => {
  closeMenu()
  router.push('/export')
}

const doLogout = async () => {
  closeMenu()
  await handleLogout()
  router.push('/')
}

// 滚动时加深头部背景
const onScroll = () => {
  scrolled.value = window.scrollY > 12
}

// 点击外部关闭菜单
const onDocClick = (e) => {
  if (menuRef.value && !menuRef.value.contains(e.target)) {
    closeMenu()
  }
}

onMounted(() => {
  refreshLoginUser()
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('click', onDocClick)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('click', onDocClick)
})
</script>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(250, 247, 241, 0.72);
  backdrop-filter: blur(18px) saturate(1.4);
  -webkit-backdrop-filter: blur(18px) saturate(1.4);
  border-bottom: 1px solid transparent;
  transition: border-color var(--dur-med) var(--ease-out), box-shadow var(--dur-med) var(--ease-out);
}

.app-header.is-scrolled {
  border-bottom-color: var(--line);
  box-shadow: 0 6px 28px rgba(80, 60, 40, 0.06);
}

.header-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 28px;
  height: 68px;
  display: flex;
  align-items: center;
  gap: 36px;
}

/* 品牌 */
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.brand-mark {
  position: relative;
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: var(--grad-rose);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-rose);
}

.brand-mark::after {
  content: '';
  position: absolute;
  inset: 5px;
  border-radius: 8px;
  border: 1.5px solid rgba(255, 255, 255, 0.75);
  opacity: 0.9;
}

.brand-mark-core {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--white);
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.25);
}

.brand-name {
  font-family: var(--font-display);
  font-size: 1.22rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--ink);
}

.brand-name em {
  font-style: normal;
  font-family: var(--font-body);
  font-weight: 700;
  font-size: 0.92em;
  background: var(--grad-rose);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin-left: 2px;
}

/* 导航 */
.main-nav {
  display: flex;
  gap: 6px;
  flex: 1;
}

.nav-link {
  position: relative;
  padding: 8px 14px;
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--ink-soft);
  border-radius: 10px;
  transition: color var(--dur-fast) var(--ease-out), background var(--dur-fast) var(--ease-out);
}

.nav-link:hover {
  color: var(--ink);
  background: rgba(224, 90, 114, 0.06);
}

.nav-link.active {
  color: var(--rose-deep);
  background: rgba(224, 90, 114, 0.09);
}

.nav-link.active::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 3px;
  height: 2px;
  border-radius: 2px;
  background: var(--grad-rose);
}

/* 用户区域 */
.user-area {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.user-menu {
  position: relative;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px 6px 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--white);
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast);
}

.user-trigger:hover {
  border-color: rgba(224, 90, 114, 0.45);
  box-shadow: var(--shadow-soft);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--grad-rose);
  color: var(--white);
  font-size: 0.92rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
}

.user-name {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--ink);
}

.chevron {
  color: var(--ink-faint);
  transition: transform var(--dur-fast) var(--ease-out);
}

.chevron.open {
  transform: rotate(180deg);
}

/* 下拉菜单 */
.dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 10px);
  width: 232px;
  background: var(--white);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-float);
  padding: 8px;
  overflow: hidden;
}

.dropdown-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
}

.dropdown-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--grad-rose);
  color: var(--white);
  font-weight: 700;
  font-size: 1.05rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  flex-shrink: 0;
}

.dropdown-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.dropdown-name {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-account {
  font-size: 0.78rem;
  color: var(--ink-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-divider {
  height: 1px;
  background: var(--line);
  margin: 6px 4px;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  border-radius: 10px;
  font-size: 0.9rem;
  color: var(--ink-soft);
  text-align: left;
  transition: background var(--dur-fast), color var(--dur-fast);
}

.dropdown-item:hover {
  background: var(--paper-deep);
  color: var(--ink);
}

.dropdown-item.danger {
  color: var(--rose-deep);
}

.dropdown-item.danger:hover {
  background: rgba(224, 90, 114, 0.08);
}

/* 登录按钮 */
.login-btn {
  padding: 9px 20px;
  border-radius: 999px;
  background: var(--grad-rose);
  color: var(--white);
  font-size: 0.9rem;
  font-weight: 600;
  box-shadow: var(--shadow-rose);
  transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast);
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 40px rgba(224, 90, 114, 0.32);
}

/* 菜单弹出动画 */
.menu-pop-enter-active {
  transition: opacity var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
  transform-origin: top right;
}

.menu-pop-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
  transform-origin: top right;
}

.menu-pop-enter-from,
.menu-pop-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.96);
}

/* 移动端 */
@media (max-width: 768px) {
  .header-inner {
    padding: 0 16px;
    height: 60px;
    gap: 14px;
  }

  .main-nav {
    gap: 2px;
  }

  .nav-link {
    padding: 8px 10px;
    font-size: 0.88rem;
  }

  .user-name {
    display: none;
  }
}

@media (max-width: 560px) {
  .nav-link:nth-child(1) {
    display: none; /* 首页入口在移动端隐藏，点击 Logo 即可返回 */
  }

  .nav-link {
    padding: 8px 8px;
    font-size: 0.84rem;
  }

  .nav-link.active::after {
    left: 8px;
    right: 8px;
  }

  .brand-name {
    font-size: 1.08rem;
  }
}
</style>
