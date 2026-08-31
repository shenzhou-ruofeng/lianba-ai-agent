<template>
  <div class="home">
    <!-- 顶部导航 -->
    <AppHeader />

    <main class="home-main">
      <!-- ========== Hero 区 ========== -->
      <section class="hero" aria-label="平台介绍">
        <!-- 背景氛围 -->
        <div class="hero-bg" aria-hidden="true">
          <span class="glow glow-rose"></span>
          <span class="glow glow-amber"></span>
          <span class="glow glow-sage"></span>
          <span v-for="i in 14" :key="i" class="particle" :style="particleStyle(i)"></span>
          <span class="grain"></span>
        </div>

        <div class="hero-inner">
          <p class="eyebrow reveal" style="--stagger: 0ms">
            <span class="eyebrow-line"></span>
            恋吧 AI 超级智能体应用平台
          </p>

          <h1 class="hero-title">
            <span class="title-line reveal" style="--stagger: 120ms">
              <span class="title-cn">让 AI 成为你的</span>
              <span class="title-accent">知心伴侣</span>
            </span>
            <span class="title-line reveal" style="--stagger: 260ms">
              <span class="title-cn">与全能助手</span>
              <span class="title-cn-dim">—— 恋吧 · AI 应用平台</span>
            </span>
          </h1>

          <p class="hero-sub reveal" style="--stagger: 400ms">
            AI 恋爱大师陪你梳理情感困惑，AI 超级智能体帮你解决专业难题。
            基于 RAG 知识库检索增强与多模态能力，每一次对话都更懂你。
          </p>

          <div class="hero-actions reveal" style="--stagger: 540ms">
            <router-link to="/love-master" class="cta cta-primary">
              <span class="cta-icon" aria-hidden="true">♡</span>
              体验 AI 恋爱大师
            </router-link>
            <router-link to="/super-agent" class="cta cta-ghost">
              <span class="cta-icon" aria-hidden="true">✦</span>
              进入 AI 超级智能体
            </router-link>
          </div>

          <div class="hero-metrics reveal" style="--stagger: 680ms">
            <div class="metric">
              <span class="metric-num">7×24</span>
              <span class="metric-label">全天候在线</span>
            </div>
            <div class="metric-divider" aria-hidden="true"></div>
            <div class="metric">
              <span class="metric-num">多模态</span>
              <span class="metric-label">图文语音理解</span>
            </div>
            <div class="metric-divider" aria-hidden="true"></div>
            <div class="metric">
              <span class="metric-num">RAG</span>
              <span class="metric-label">知识库检索增强</span>
            </div>
          </div>
        </div>

        <!-- 向下滚动提示 -->
        <button class="scroll-hint" aria-label="向下滚动查看详情" @click="scrollToFeatures">
          <span class="scroll-mouse" aria-hidden="true"><span class="scroll-wheel"></span></span>
        </button>
      </section>

      <!-- ========== 能力特性区 ========== -->
      <section class="features" aria-label="平台能力">
        <div class="section-head scroll-reveal">
          <p class="section-eyebrow">Capabilities</p>
          <h2 class="section-title">不止聊天，更是<span class="text-gradient">全能 AI 工作台</span></h2>
          <p class="section-desc">从情感咨询到专业问答，从图文理解到报告导出，一站搞定</p>
        </div>

        <div class="feature-grid">
          <article v-for="(f, i) in features" :key="f.title" class="feature-card scroll-reveal" :style="{ transitionDelay: (i % 3) * 90 + 'ms' }">
            <span class="feature-index" aria-hidden="true">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="feature-icon" :style="{ background: f.bg }" aria-hidden="true">{{ f.icon }}</span>
            <h3 class="feature-title">{{ f.title }}</h3>
            <p class="feature-desc">{{ f.desc }}</p>
            <span class="feature-corner" aria-hidden="true"></span>
          </article>
        </div>
      </section>

      <!-- ========== 智能体产品区 ========== -->
      <section class="agents" aria-label="智能体应用">
        <div class="section-head scroll-reveal">
          <p class="section-eyebrow">Agents</p>
          <h2 class="section-title">两大智能体，<span class="text-gradient">各有专长</span></h2>
          <p class="section-desc">选择适合你的助手，开启一段有温度的 AI 对话</p>
        </div>

        <div class="agent-showcase">
          <!-- AI 恋爱大师 -->
          <article class="agent-card agent-love scroll-reveal">
            <div class="agent-glow" aria-hidden="true"></div>
            <div class="agent-top">
              <span class="agent-badge">情感 · RAG 增强</span>
              <span class="agent-emoji" aria-hidden="true">♡</span>
            </div>
            <h3 class="agent-name">AI 恋爱大师</h3>
            <p class="agent-desc">
              基于候选人知识库的恋爱对象推荐与情感咨询，支持生成专属恋爱报告，一键导出 PDF / Word / Markdown。
            </p>
            <ul class="agent-points">
              <li><span class="point-mark" aria-hidden="true">✦</span>恋爱咨询与对象推荐双模式</li>
              <li><span class="point-mark" aria-hidden="true">✦</span>性别硬过滤，精准匹配</li>
              <li><span class="point-mark" aria-hidden="true">✦</span>恋爱报告生成与多格式导出</li>
            </ul>
            <router-link to="/love-master" class="agent-cta">
              与恋爱大师对话
              <span class="agent-arrow" aria-hidden="true">→</span>
            </router-link>
          </article>

          <!-- AI 超级智能体 -->
          <article class="agent-card agent-super scroll-reveal" :style="{ transitionDelay: '140ms' }">
            <div class="agent-glow" aria-hidden="true"></div>
            <div class="agent-top">
              <span class="agent-badge">全能 · 工具调用</span>
              <span class="agent-emoji" aria-hidden="true">✦</span>
            </div>
            <h3 class="agent-name">AI 超级智能体</h3>
            <p class="agent-desc">
              能思考、能行动的智能体：联网搜索、网页抓取、图片理解与生成、PDF 生成、邮件发送，把任务交给它，把结果留给自己。
            </p>
            <ul class="agent-points">
              <li><span class="point-mark" aria-hidden="true">✦</span>深度思考过程可视化</li>
              <li><span class="point-mark" aria-hidden="true">✦</span>多模态图文理解与生成</li>
              <li><span class="point-mark" aria-hidden="true">✦</span>十余种实用工具自由调用</li>
            </ul>
            <router-link to="/super-agent" class="agent-cta">
              与超级智能体对话
              <span class="agent-arrow" aria-hidden="true">→</span>
            </router-link>
          </article>
        </div>
      </section>

      <!-- ========== 使用流程区 ========== -->
      <section class="steps" aria-label="使用流程">
        <div class="section-head scroll-reveal">
          <p class="section-eyebrow">How it works</p>
          <h2 class="section-title">三步开启<span class="text-gradient">智能对话</span></h2>
        </div>

        <div class="step-row">
          <div v-for="(s, i) in steps" :key="s.title" class="step scroll-reveal" :style="{ transitionDelay: i * 120 + 'ms' }">
            <div class="step-num" aria-hidden="true">{{ i + 1 }}</div>
            <h3 class="step-title">{{ s.title }}</h3>
            <p class="step-desc">{{ s.desc }}</p>
            <span v-if="i < steps.length - 1" class="step-connector" aria-hidden="true"></span>
          </div>
        </div>
      </section>

      <!-- ========== SEO 内容区 ========== -->
      <section class="seo-content" aria-label="平台介绍详情">
        <div class="seo-inner scroll-reveal">
          <div class="seo-text">
            <h2>恋吧 AI 超级智能体应用平台，让对话更有价值</h2>
            <p>
              恋吧 AI 超级智能体应用平台（Lianba AI Agent Platform）是集情感咨询、专业问答、多模态理解与文件导出于一体的智能对话平台。
              <strong>AI 恋爱大师</strong>基于 RAG 知识库检索增强技术，从候选人知识库中精准推荐恋爱对象，并生成结构化恋爱报告；
              <strong>AI 超级智能体</strong>具备深度思考与工具调用能力，可完成联网搜索、网页抓取、图片生成、PDF 文档生成等复杂任务。
            </p>
            <p>
              平台支持语音输入、图片理解、会话历史管理与多格式导出（PDF / Word / Markdown），
              无论是寻找情感建议，还是解决工作学习中的专业问题，恋吧 AI 都能提供高效、可靠的智能服务。
            </p>
          </div>
          <div class="seo-facts">
            <div class="fact-row"><span class="fact-key">支持应用</span><span class="fact-val">恋爱大师 · 超级智能体</span></div>
            <div class="fact-row"><span class="fact-key">知识检索</span><span class="fact-val">RAG 混合检索增强</span></div>
            <div class="fact-row"><span class="fact-key">多模态</span><span class="fact-val">图片 / 语音 / 文档</span></div>
            <div class="fact-row"><span class="fact-key">导出格式</span><span class="fact-val">PDF / Word / Markdown</span></div>
            <div class="fact-row"><span class="fact-key">访问方式</span><span class="fact-val">Web 在线使用</span></div>
          </div>
        </div>
      </section>

      <!-- ========== 底部 CTA ========== -->
      <section class="final-cta scroll-reveal">
        <div class="final-inner">
          <h2 class="final-title">准备好开始了吗？</h2>
          <p class="final-sub">登录后即可与两大智能体畅聊，历史会话随时回顾、一键导出</p>
          <div class="final-actions">
            <router-link v-if="!isLoggedIn" to="/login" class="cta cta-primary">立即登录体验</router-link>
            <router-link v-else to="/love-master" class="cta cta-primary">开始对话</router-link>
            <router-link to="/export" class="cta cta-ghost">管理我的会话</router-link>
          </div>
        </div>
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { useHead } from '@vueuse/head'
import AppHeader from '../components/AppHeader.vue'
import AppFooter from '../components/AppFooter.vue'
import { useAuth } from '../composables/useAuth'

// SEO / GEO 元数据
useHead({
  title: '恋吧AI超级智能体应用平台 - AI恋爱大师与AI超级智能体',
  meta: [
    {
      name: 'description',
      content: '恋吧AI超级智能体应用平台，提供AI恋爱大师情感咨询与AI超级智能体全能问答服务。支持RAG知识库检索、多模态对话、语音输入与PDF报告导出。'
    },
    {
      name: 'keywords',
      content: 'AI智能体,AI恋爱大师,恋爱咨询,情感顾问,AI助手,智能对话,恋吧,AI超级智能体,在线问答,AI报告导出'
    },
    {
      property: 'og:title',
      content: '恋吧AI超级智能体应用平台 - AI恋爱大师与AI超级智能体'
    },
    {
      property: 'og:description',
      content: 'AI恋爱大师陪你聊情感、AI超级智能体帮你解难题，支持多模态对话与会话报告导出。'
    }
  ]
})

const { isLoggedIn, refreshLoginUser } = useAuth()

// 特性数据
const features = [
  {
    icon: '♡',
    bg: 'linear-gradient(135deg, #fdeef0, #fbe4e0)',
    title: 'RAG 知识库增强',
    desc: '恋爱大师接入向量检索与关键词全文检索，混合检索结果经 RRF 融合排序，让回答更精准、更有据可依。'
  },
  {
    icon: '◈',
    bg: 'linear-gradient(135deg, #eef5f2, #e3eeea)',
    title: '多模态对话',
    desc: '支持图片理解、语音输入与文档解析，让 AI 看得见、听得懂、读得会，交流方式更自然。'
  },
  {
    icon: '❋',
    bg: 'linear-gradient(135deg, #fdf3ec, #f9e8dd)',
    title: '会话报告导出',
    desc: '恋爱报告与完整对话记录均可导出为 PDF / Word / Markdown，重要内容随时存档分享。'
  },
  {
    icon: '✦',
    bg: 'linear-gradient(135deg, #f5eefc, #efe6f8)',
    title: '深度思考智能体',
    desc: '超级智能体具备分步推理与工具调用能力，思考过程清晰可见，执行结果真实可靠。'
  },
  {
    icon: '⌘',
    bg: 'linear-gradient(135deg, #e8f0f8, #dfebf5)',
    title: '十余种工具调用',
    desc: '联网搜索、网页抓取、PDF 生成、邮件发送、数据库操作……把复杂任务交给工具，一键完成。'
  },
  {
    icon: '☷',
    bg: 'linear-gradient(135deg, #fdeef4, #f9e4ec)',
    title: '会话历史管理',
    desc: '每个智能体独立维护会话列表，随时切换历史对话、新建会话，重要记录一键导出备份。'
  }
]

const steps = [
  { title: '注册登录', desc: '使用邮箱快速注册，验证码登录，会话数据与账号绑定，安全私密。' },
  { title: '选择智能体', desc: '按需选择 AI 恋爱大师或 AI 超级智能体，开启你的专属对话。' },
  { title: '对话与导出', desc: '畅快对话后，可在会话管理中回顾历史，并导出为 PDF / Word / Markdown。' }
]

// 粒子样式
const particleStyle = (i) => {
  const size = 3 + ((i * 7) % 6)
  const left = ((i * 53) % 100)
  const delay = (i * 0.9) % 8
  const duration = 9 + ((i * 3) % 8)
  return {
    width: size + 'px',
    height: size + 'px',
    left: left + '%',
    top: (20 + ((i * 31) % 60)) + '%',
    animationDelay: delay + 's',
    animationDuration: duration + 's'
  }
}

// 滚动渐显
let observer = null

const scrollToFeatures = () => {
  const el = document.querySelector('.features')
  if (el) el.scrollIntoView({ behavior: 'smooth' })
}

onMounted(() => {
  refreshLoginUser()
  // 入场动画：hero 元素依次显现
  requestAnimationFrame(() => {
    document.querySelectorAll('.hero .reveal').forEach(el => el.classList.add('is-visible'))
  })
  // 滚动渐显
  observer = new IntersectionObserver(
    entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          observer.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.12, rootMargin: '0px 0px -40px 0px' }
  )
  document.querySelectorAll('.scroll-reveal').forEach(el => observer.observe(el))
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})
</script>

<style scoped>
.home {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--paper);
}

.home-main {
  flex: 1;
}

/* ========== Hero ========== */
.hero {
  position: relative;
  min-height: calc(100vh - 68px);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 80px 24px 100px;
}

.hero-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
  animation: breathe 9s ease-in-out infinite;
}

.glow-rose {
  width: 480px;
  height: 480px;
  background: radial-gradient(circle, rgba(224, 90, 114, 0.32), transparent 65%);
  top: -120px;
  right: -80px;
}

.glow-amber {
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(240, 160, 92, 0.28), transparent 65%);
  bottom: -140px;
  left: -100px;
  animation-delay: 2.5s;
}

.glow-sage {
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(46, 125, 116, 0.2), transparent 65%);
  top: 30%;
  left: 42%;
  animation-delay: 5s;
}

.particle {
  position: absolute;
  border-radius: 50%;
  background: var(--rose);
  opacity: 0.16;
  animation: float-slow ease-in-out infinite;
}

.grain {
  position: absolute;
  inset: 0;
  opacity: 0.35;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120' viewBox='0 0 120 120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='120' height='120' filter='url(%23n)' opacity='0.045'/%3E%3C/svg%3E");
  pointer-events: none;
}

.hero-inner {
  position: relative;
  z-index: 2;
  max-width: 900px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.eyebrow {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.85rem;
  letter-spacing: 0.22em;
  color: var(--rose-deep);
  font-weight: 600;
  margin-bottom: 30px;
}

.eyebrow-line {
  width: 36px;
  height: 1px;
  background: var(--grad-rose);
}

.hero-title {
  font-family: var(--font-display);
  font-weight: 900;
  font-size: clamp(2.2rem, 5.6vw, 4.2rem);
  line-height: 1.28;
  letter-spacing: 0.02em;
  color: var(--ink);
  margin-bottom: 28px;
}

.title-line {
  display: block;
}

.title-accent {
  position: relative;
  background: var(--grad-rose);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin: 0 6px;
}

.title-accent::after {
  content: '';
  position: absolute;
  left: 4px;
  right: 4px;
  bottom: -4px;
  height: 10px;
  background: linear-gradient(90deg, rgba(224, 90, 114, 0.16), rgba(240, 160, 92, 0.16));
  border-radius: 6px;
  z-index: -1;
}

.title-cn-dim {
  color: var(--ink-soft);
  font-weight: 600;
  font-size: 0.72em;
  margin-left: 10px;
}

.hero-sub {
  max-width: 620px;
  font-size: 1.06rem;
  line-height: 1.9;
  color: var(--ink-soft);
  margin-bottom: 42px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 56px;
}

.cta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 15px 34px;
  border-radius: 999px;
  font-size: 1rem;
  font-weight: 600;
  transition: transform var(--dur-med) var(--ease-out), box-shadow var(--dur-med);
}

.cta-icon {
  font-size: 1.15rem;
  line-height: 1;
}

.cta-primary {
  background: var(--grad-rose);
  color: var(--white);
  box-shadow: var(--shadow-rose);
}

.cta-primary:hover {
  transform: translateY(-3px);
  box-shadow: 0 20px 46px rgba(224, 90, 114, 0.36);
}

.cta-ghost {
  background: var(--white);
  color: var(--ink);
  border: 1.5px solid var(--line);
}

.cta-ghost:hover {
  transform: translateY(-3px);
  border-color: rgba(224, 90, 114, 0.4);
  box-shadow: var(--shadow-soft);
}

.hero-metrics {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 22px 40px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.metric-num {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--ink);
}

.metric-label {
  font-size: 0.78rem;
  color: var(--ink-faint);
}

.metric-divider {
  width: 1px;
  height: 32px;
  background: var(--line);
}

.scroll-hint {
  position: absolute;
  bottom: 26px;
  left: 50%;
  transform: translateX(-50%);
  border: none;
  background: transparent;
  z-index: 2;
  padding: 8px;
}

.scroll-mouse {
  display: block;
  width: 24px;
  height: 38px;
  border: 2px solid var(--ink-faint);
  border-radius: 14px;
  position: relative;
}

.scroll-wheel {
  position: absolute;
  left: 50%;
  top: 7px;
  width: 3px;
  height: 7px;
  margin-left: -1.5px;
  border-radius: 2px;
  background: var(--rose);
  animation: wheel-drop 1.8s ease-in-out infinite;
}

@keyframes wheel-drop {
  0% { opacity: 1; transform: translateY(0); }
  70% { opacity: 0; transform: translateY(14px); }
  100% { opacity: 0; transform: translateY(0); }
}

/* ========== 通用章节 ========== */
.section-head {
  text-align: center;
  max-width: 640px;
  margin: 0 auto 56px;
}

.section-eyebrow {
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--amber);
  margin-bottom: 14px;
}

.section-title {
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 3.4vw, 2.4rem);
  font-weight: 700;
  color: var(--ink);
  line-height: 1.4;
  margin-bottom: 14px;
}

.text-gradient {
  background: var(--grad-rose);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin: 0 4px;
}

.section-desc {
  color: var(--ink-soft);
  font-size: 1rem;
  line-height: 1.8;
}

/* ========== 特性区 ========== */
.features {
  padding: 110px 28px 90px;
  background: var(--grad-soft);
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.feature-grid {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 22px;
}

.feature-card {
  position: relative;
  padding: 34px 30px 30px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid var(--line);
  transition: transform var(--dur-med) var(--ease-out), box-shadow var(--dur-med), border-color var(--dur-med);
  overflow: hidden;
}

.feature-card:hover {
  transform: translateY(-8px);
  box-shadow: var(--shadow-float);
  border-color: rgba(224, 90, 114, 0.28);
}

.feature-index {
  position: absolute;
  top: 18px;
  right: 22px;
  font-family: var(--font-display);
  font-size: 2.4rem;
  font-weight: 700;
  color: rgba(38, 34, 28, 0.06);
}

.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  font-size: 1.4rem;
  margin-bottom: 20px;
  color: var(--ink);
}

.feature-title {
  font-family: var(--font-display);
  font-size: 1.18rem;
  font-weight: 700;
  margin-bottom: 10px;
  color: var(--ink);
}

.feature-desc {
  font-size: 0.92rem;
  line-height: 1.8;
  color: var(--ink-soft);
}

.feature-corner {
  position: absolute;
  right: -34px;
  bottom: -34px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 1.5px dashed rgba(224, 90, 114, 0.25);
  transition: transform var(--dur-slow) var(--ease-out);
}

.feature-card:hover .feature-corner {
  transform: scale(1.6) rotate(40deg);
}

/* ========== 智能体区 ========== */
.agents {
  padding: 110px 28px 90px;
}

.agent-showcase {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 26px;
}

.agent-card {
  position: relative;
  padding: 42px 40px 36px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: transform var(--dur-med) var(--ease-out), box-shadow var(--dur-med);
}

.agent-card:hover {
  transform: translateY(-8px);
  box-shadow: var(--shadow-float);
}

.agent-love {
  background: linear-gradient(165deg, #fdeef1 0%, #fdf6f1 70%);
  border: 1px solid rgba(224, 90, 114, 0.18);
}

.agent-super {
  background: linear-gradient(165deg, #eef5f2 0%, #f4f7f2 70%);
  border: 1px solid rgba(46, 125, 116, 0.18);
}

.agent-glow {
  position: absolute;
  top: -80px;
  right: -80px;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.5;
  pointer-events: none;
}

.agent-love .agent-glow {
  background: radial-gradient(circle, rgba(224, 90, 114, 0.3), transparent 70%);
}

.agent-super .agent-glow {
  background: radial-gradient(circle, rgba(46, 125, 116, 0.28), transparent 70%);
}

.agent-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26px;
}

.agent-badge {
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(38, 34, 28, 0.08);
  color: var(--ink-soft);
}

.agent-emoji {
  font-size: 2rem;
  line-height: 1;
}

.agent-love .agent-emoji {
  color: var(--rose);
}

.agent-super .agent-emoji {
  color: var(--sage);
}

.agent-name {
  font-family: var(--font-display);
  font-size: 1.7rem;
  font-weight: 700;
  margin-bottom: 14px;
  color: var(--ink);
}

.agent-desc {
  font-size: 0.95rem;
  line-height: 1.85;
  color: var(--ink-soft);
  margin-bottom: 22px;
  min-height: 88px;
}

.agent-points {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 30px;
}

.agent-points li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.9rem;
  color: var(--ink-soft);
}

.point-mark {
  color: var(--rose);
  font-size: 0.8rem;
}

.agent-super .point-mark {
  color: var(--sage);
}

.agent-cta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--ink);
  transition: gap var(--dur-fast) var(--ease-out);
}

.agent-cta:hover {
  gap: 16px;
}

.agent-love .agent-cta {
  color: var(--rose-deep);
}

.agent-super .agent-cta {
  color: var(--sage);
}

/* ========== 流程区 ========== */
.steps {
  padding: 100px 28px 110px;
  background: var(--grad-soft);
  border-top: 1px solid var(--line);
}

.step-row {
  max-width: 1020px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 34px;
  counter-reset: step;
}

.step {
  position: relative;
  text-align: center;
  padding: 10px 20px;
}

.step-num {
  width: 56px;
  height: 56px;
  margin: 0 auto 20px;
  border-radius: 50%;
  background: var(--white);
  border: 1.5px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--rose-deep);
  box-shadow: var(--shadow-soft);
  position: relative;
  z-index: 1;
}

.step-title {
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  margin-bottom: 10px;
  color: var(--ink);
}

.step-desc {
  font-size: 0.9rem;
  line-height: 1.8;
  color: var(--ink-soft);
  max-width: 260px;
  margin: 0 auto;
}

.step-connector {
  position: absolute;
  top: 37px;
  left: calc(50% + 46px);
  width: calc(100% - 92px);
  height: 1.5px;
  background: linear-gradient(90deg, rgba(224, 90, 114, 0.35), rgba(240, 160, 92, 0.35));
}

.step-connector::after {
  content: '';
  position: absolute;
  right: 0;
  top: -2.5px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--amber);
  opacity: 0.7;
}

/* ========== SEO 内容区 ========== */
.seo-content {
  padding: 100px 28px;
}

.seo-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 60px;
  align-items: start;
  padding: 50px 54px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: var(--white);
  box-shadow: var(--shadow-soft);
}

.seo-text h2 {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 18px;
  color: var(--ink);
}

.seo-text p {
  font-size: 0.95rem;
  line-height: 2;
  color: var(--ink-soft);
  margin-bottom: 14px;
}

.seo-text strong {
  color: var(--rose-deep);
  font-weight: 600;
}

.seo-facts {
  border-left: 2px solid var(--grad-rose);
  padding-left: 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fact-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--line);
}

.fact-key {
  font-size: 0.85rem;
  color: var(--ink-faint);
  flex-shrink: 0;
}

.fact-val {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--ink);
  text-align: right;
}

/* ========== 底部 CTA ========== */
.final-cta {
  padding: 0 28px 110px;
}

.final-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 72px 40px;
  text-align: center;
  border-radius: var(--radius-lg);
  background: linear-gradient(140deg, #2a2420 0%, #3d3128 55%, #4a3a2c 100%);
  position: relative;
  overflow: hidden;
}

.final-inner::before {
  content: '';
  position: absolute;
  top: -120px;
  right: -80px;
  width: 360px;
  height: 360px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(224, 90, 114, 0.35), transparent 65%);
  filter: blur(40px);
}

.final-inner::after {
  content: '';
  position: absolute;
  bottom: -140px;
  left: -60px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(240, 160, 92, 0.25), transparent 65%);
  filter: blur(40px);
}

.final-title {
  position: relative;
  z-index: 1;
  font-family: var(--font-display);
  font-size: clamp(1.7rem, 3.6vw, 2.5rem);
  font-weight: 700;
  color: var(--white);
  margin-bottom: 16px;
}

.final-sub {
  position: relative;
  z-index: 1;
  color: rgba(255, 255, 255, 0.72);
  font-size: 1rem;
  margin-bottom: 36px;
}

.final-actions {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
}

.final-actions .cta-ghost {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.24);
  color: var(--white);
}

.final-actions .cta-ghost:hover {
  border-color: rgba(255, 255, 255, 0.5);
}

/* ========== 响应式 ========== */
@media (max-width: 900px) {
  .feature-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .agent-showcase {
    grid-template-columns: 1fr;
  }

  .seo-inner {
    grid-template-columns: 1fr;
    gap: 36px;
    padding: 36px 28px;
  }

  .seo-facts {
    border-left: none;
    border-top: 2px solid rgba(224, 90, 114, 0.3);
    padding-left: 0;
    padding-top: 24px;
  }
}

@media (max-width: 640px) {
  .hero {
    padding: 60px 20px 90px;
    min-height: calc(100vh - 60px);
  }

  .hero-metrics {
    gap: 18px;
    padding: 18px 24px;
  }

  .metric-num {
    font-size: 1.2rem;
  }

  .hero-actions {
    flex-direction: column;
    width: 100%;
    max-width: 320px;
  }

  .cta {
    justify-content: center;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }

  .features,
  .agents,
  .steps,
  .seo-content {
    padding: 70px 20px;
  }

  .step-row {
    grid-template-columns: 1fr;
    gap: 44px;
  }

  .step-connector {
    display: none;
  }

  .agent-card {
    padding: 32px 26px 28px;
  }

  .agent-desc {
    min-height: 0;
  }

  .final-inner {
    padding: 56px 26px;
  }
}
</style>
