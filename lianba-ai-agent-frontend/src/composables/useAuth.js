import { ref, computed } from 'vue'
import { getLoginUser, userLogout, updateUserNickname } from '../api'

/**
 * 全局登录态管理（模块级单例）
 * 路由守卫与 AppHeader 用户菜单共享同一份状态，
 * 登录 / 登出 / 刷新后只需调用对应方法，各组件自动响应。
 */
const loginUser = ref(null)
// 加载状态，避免并发请求重复拉取
let loadingPromise = null

const isLoggedIn = computed(() => !!loginUser.value)

/**
 * 拉取当前登录用户（带缓存：已有登录态时直接返回，不重复请求）
 */
const refreshLoginUser = async (force = false) => {
  if (loginUser.value && !force) {
    return loginUser.value
  }
  if (loadingPromise && !force) {
    return loadingPromise
  }
  loadingPromise = getLoginUser()
    .then(res => {
      loginUser.value = res.code === 0 && res.data ? res.data : null
      return loginUser.value
    })
    .catch(() => {
      loginUser.value = null
      return null
    })
    .finally(() => {
      loadingPromise = null
    })
  return loadingPromise
}

/**
 * 退出登录：调用后端注销接口并清空本地登录态
 */
const handleLogout = async () => {
  try {
    await userLogout()
  } catch (e) {
    // 后端注销失败不阻塞前端登出
  }
  loginUser.value = null
}

/**
 * 更新昵称：调用后端接口并同步全局登录态，
 * AppHeader 等所有读取 loginUser 的组件自动刷新显示
 */
const updateNickname = async (userName) => {
  const res = await updateUserNickname(userName)
  if (res.code === 0 && res.data) {
    loginUser.value = { ...loginUser.value, ...res.data }
    return res.data
  }
  throw new Error(res.message || '昵称保存失败')
}

export function useAuth() {
  return {
    loginUser,
    isLoggedIn,
    refreshLoginUser,
    handleLogout,
    updateNickname
  }
}

// 命名导出：便于路由守卫等模块直接使用模块级单例
// eslint-disable-next-line no-unused-vars
export { loginUser, isLoggedIn, refreshLoginUser, handleLogout, updateNickname }
