<template>
  <div class="login-container">
    <div class="login-card">
      <h1 class="login-title">恋吧AI超级智能体</h1>
      <p class="login-subtitle">{{ isRegister ? '注册账号，开启 AI 之旅' : '登录后即可使用 AI 服务' }}</p>

      <div class="login-form">
        <div class="form-item">
          <label class="form-label">{{ isRegister ? '邮箱' : '账号/邮箱' }}</label>
          <input
            v-model.trim="form.userAccount"
            class="form-input"
            type="text"
            :placeholder="isRegister ? '请输入邮箱（将作为登录账号）' : '请输入账号或邮箱'"
            @keyup.enter="handleSubmit"
          />
        </div>
        <div v-if="isRegister" class="form-item">
          <label class="form-label">邮箱验证码</label>
          <div class="code-row">
            <input
              v-model.trim="form.emailCode"
              class="form-input code-input"
              type="text"
              maxlength="6"
              placeholder="请输入 6 位验证码"
              @keyup.enter="handleSubmit"
            />
            <button class="code-btn" :disabled="codeSending || codeCountdown > 0 || !isValidEmail(form.userAccount)" @click="handleSendCode">
              <span v-if="codeSending" class="btn-spinner"></span>
              {{ codeCountdown > 0 ? `${codeCountdown}s 后重发` : '获取验证码' }}
            </button>
          </div>
          <div v-if="devCodeHint" class="dev-code-hint">{{ devCodeHint }}</div>
        </div>
        <div class="form-item">
          <label class="form-label">密码</label>
          <input
            v-model.trim="form.userPassword"
            class="form-input"
            type="password"
            placeholder="请输入密码（至少 8 位）"
            @keyup.enter="handleSubmit"
          />
        </div>
        <div v-if="isRegister" class="form-item">
          <label class="form-label">确认密码</label>
          <input
            v-model.trim="form.checkPassword"
            class="form-input"
            type="password"
            placeholder="请再次输入密码"
            @keyup.enter="handleSubmit"
          />
        </div>

        <div v-if="errorMsg" class="form-error">{{ errorMsg }}</div>

        <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
          <span v-if="submitting" class="btn-spinner"></span>
          {{ submitting ? '请稍候...' : (isRegister ? '注册' : '登录') }}
        </button>

        <div class="switch-mode">
          <template v-if="isRegister">
            已有账号？<a @click="switchMode(false)">去登录</a>
          </template>
          <template v-else>
            还没有账号？<a @click="switchMode(true)">去注册</a>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { userLogin, userRegister, sendEmailCode } from '../api'

useHead({
  title: '登录 - 恋吧AI超级智能体应用平台'
})

const router = useRouter()
const route = useRoute()

const isRegister = ref(false)
const submitting = ref(false)
const errorMsg = ref('')
// 验证码发送状态与倒计时
const codeSending = ref(false)
const codeCountdown = ref(0)
const devCodeHint = ref('')
let countdownTimer = null

const form = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  emailCode: ''
})

// 简单邮箱格式校验
const isValidEmail = (email) => /^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/.test(email || '')

const switchMode = (register) => {
  isRegister.value = register
  errorMsg.value = ''
  devCodeHint.value = ''
  if (!register) {
    form.emailCode = ''
  }
}

// 发送邮箱验证码（60 秒倒计时）
const handleSendCode = async () => {
  if (!isValidEmail(form.userAccount)) {
    errorMsg.value = '请输入正确的邮箱地址'
    return
  }
  if (codeSending.value || codeCountdown.value > 0) return
  codeSending.value = true
  errorMsg.value = ''
  devCodeHint.value = ''
  try {
    const res = await sendEmailCode(form.userAccount)
    if (res.code !== 0) {
      errorMsg.value = res.message || '验证码发送失败'
      return
    }
    // 未配置邮件服务时后端降级返回验证码，自动填入并提示（本地开发模式）
    if (res.data) {
      form.emailCode = res.data
      devCodeHint.value = `开发模式：邮件服务未配置，验证码已自动填入（${res.data}）`
    } else {
      devCodeHint.value = '验证码已发送至邮箱，请查收（5 分钟内有效）'
    }
    codeCountdown.value = 60
    countdownTimer = setInterval(() => {
      codeCountdown.value -= 1
      if (codeCountdown.value <= 0) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || '验证码发送失败，请稍后重试'
  } finally {
    codeSending.value = false
  }
}

const validate = () => {
  if (!form.userAccount) {
    errorMsg.value = isRegister.value ? '请输入邮箱' : '请输入账号或邮箱'
    return false
  }
  if (isRegister.value && !isValidEmail(form.userAccount)) {
    errorMsg.value = '邮箱格式不正确'
    return false
  }
  if (isRegister.value && !form.emailCode) {
    errorMsg.value = '请输入邮箱验证码'
    return false
  }
  if (!form.userPassword || form.userPassword.length < 8) {
    errorMsg.value = '密码长度不能小于 8 位'
    return false
  }
  if (isRegister.value && form.userPassword !== form.checkPassword) {
    errorMsg.value = '两次输入的密码不一致'
    return false
  }
  return true
}

const handleSubmit = async () => {
  errorMsg.value = ''
  if (!validate() || submitting.value) return
  submitting.value = true
  try {
    if (isRegister.value) {
      const res = await userRegister(form.userAccount, form.emailCode, form.userPassword, form.checkPassword)
      if (res.code !== 0) {
        errorMsg.value = res.message || '注册失败'
        return
      }
      // 注册成功后自动登录
    }
    const loginRes = await userLogin(form.userAccount, form.userPassword)
    if (loginRes.code !== 0) {
      errorMsg.value = loginRes.message || '登录失败'
      return
    }
    // 登录成功，跳转回来源页面
    const redirect = route.query.redirect || '/'
    router.push(String(redirect))
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || '网络异常，请稍后重试'
  } finally {
    submitting.value = false
  }
}

// 离开页面时清理倒计时定时器
onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #fff5f8, #fce4ec);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow:
    0 8px 32px rgba(212, 136, 158, 0.25),
    inset 0 0 0 1px rgba(255, 255, 255, 0.6);
  padding: 40px 32px;
}

.login-title {
  font-size: 1.6rem;
  font-weight: 700;
  color: #5a3d4a;
  text-align: center;
  margin: 0 0 8px;
  text-shadow: 0 0 10px rgba(212, 136, 158, 0.3);
}

.login-subtitle {
  font-size: 0.9rem;
  color: rgba(90, 61, 74, 0.7);
  text-align: center;
  margin: 0 0 28px;
}

.form-item {
  margin-bottom: 18px;
}

.form-label {
  display: block;
  font-size: 0.85rem;
  color: #5a3d4a;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 14px;
  border: 1px solid rgba(212, 136, 158, 0.4);
  border-radius: 10px;
  font-size: 0.95rem;
  color: #5a3d4a;
  background: #fff;
  outline: none;
  transition: all 0.25s;
}

.form-input:focus {
  border-color: #e8a0b4;
  box-shadow: 0 0 0 3px rgba(232, 160, 180, 0.2);
}

.form-error {
  color: #e53e5b;
  font-size: 0.85rem;
  margin-bottom: 12px;
}

/* 验证码行：输入框 + 获取按钮 */
.code-row {
  display: flex;
  gap: 8px;
}

.code-input {
  flex: 1;
}

.code-btn {
  flex-shrink: 0;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(90deg, #e8a0b4, #f8bbd0);
  color: #fff;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 108px;
}

.code-btn:hover:not(:disabled) {
  box-shadow: 0 0 10px rgba(212, 136, 158, 0.5);
}

.code-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.dev-code-hint {
  margin-top: 6px;
  font-size: 0.78rem;
  color: #b7791f;
  background: #fefce8;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 6px 10px;
  word-break: break-all;
}

.submit-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 30px;
  background: linear-gradient(90deg, #e8a0b4, #f8bbd0);
  color: #fff;
  font-size: 1rem;
  font-weight: 500;
  letter-spacing: 2px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.submit-btn:hover:not(:disabled) {
  box-shadow: 0 0 15px rgba(212, 136, 158, 0.6);
  transform: scale(1.02);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.switch-mode {
  text-align: center;
  margin-top: 18px;
  font-size: 0.88rem;
  color: rgba(90, 61, 74, 0.7);
}

.switch-mode a {
  color: #d4889e;
  cursor: pointer;
  font-weight: 500;
}

.switch-mode a:hover {
  text-decoration: underline;
}
</style>
