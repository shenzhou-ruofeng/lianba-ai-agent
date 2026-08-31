import { createRouter, createWebHistory } from 'vue-router'
import { refreshLoginUser } from '../composables/useAuth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - 恋吧AI超级智能体应用平台',
      description: '恋吧AI超级智能体应用平台提供AI恋爱大师和AI超级智能体服务，满足您的各种AI对话需求'
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: {
      title: '登录 - 恋吧AI超级智能体应用平台'
    }
  },
  {
    path: '/love-master',
    name: 'LoveMaster',
    component: () => import('../views/LoveMaster.vue'),
    meta: {
      title: 'AI恋爱大师 - 恋吧AI超级智能体应用平台',
      description: 'AI恋爱大师是恋吧AI超级智能体应用平台的专业情感顾问，帮你解答各种恋爱问题，提供情感建议',
      requiresAuth: true
    }
  },

  {
    path: '/export',
    name: 'Export',
    component: () => import('../views/Export.vue'),
    meta: {
      title: '导出会话信息 - 恋吧AI超级智能体应用平台',
      description: '将你的 AI 对话记录导出为 PDF、Word、Markdown 文件，轻松存档与管理',
      requiresAuth: true
    }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue'),
    meta: {
      title: '个人信息 - 恋吧AI',
      description: '查看和编辑你的恋吧AI个人信息',
      requiresAuth: true
    }
  },
  {
    path: '/reports',
    name: 'ReportHistory',
    component: () => import('../views/ReportHistory.vue'),
    meta: {
      title: '恋爱报告历史 - 恋吧AI',
      description: '查看 AI 为你生成的恋爱报告，支持 PDF / Word / Markdown 下载',
      requiresAuth: true
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫：设置标题 + 登录校验（与 useAuth 共享登录态）
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  // 需登录页面：校验登录态，未登录跳转登录页
  if (to.meta.requiresAuth) {
    const user = await refreshLoginUser()
    if (!user) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }
  next()
})

export default router
