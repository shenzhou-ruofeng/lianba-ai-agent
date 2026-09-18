<template>
  <div class="diary-page">
    <AppHeader />

    <main class="diary-main">
      <div class="page-head">
        <p class="page-eyebrow">Emotional Diary</p>
        <h1 class="page-title">情感日记</h1>
        <p class="page-desc">记录每天的心情，AI 帮你分析情绪变化，见证你的情感成长</p>
      </div>

      <!-- 撰写区 -->
      <section class="diary-writer">
        <div class="writer-card">
          <h2 class="writer-title">✍️ 今天的心情</h2>
          <textarea
            v-model="diaryContent"
            class="writer-textarea"
            placeholder="写下今天让你感触最深的事情..."
            rows="5"
            maxlength="5000"
          ></textarea>
          <div class="writer-meta">
            <div class="mood-selector">
              <span class="mood-label">心情</span>
              <button
                v-for="m in moodOptions"
                :key="m.value"
                class="mood-btn"
                :class="{ active: selectedMood === m.value }"
                @click="selectedMood = selectedMood === m.value ? null : m.value"
                :title="m.label"
              >{{ m.emoji }}</button>
            </div>
            <div class="tag-input-wrap">
              <input
                v-model="tagInput"
                class="tag-input"
                placeholder="添加标签（回车确认）"
                @keydown.enter.prevent="addTag"
              />
              <div class="tag-chips" v-if="diaryTags.length">
                <span v-for="(tag, i) in diaryTags" :key="i" class="tag-chip">
                  {{ tag }}
                  <button class="tag-remove" @click="diaryTags.splice(i, 1)">&times;</button>
                </span>
              </div>
            </div>
          </div>
          <div class="writer-actions">
            <span class="char-count">{{ diaryContent.length }} / 5000</span>
            <button
              class="submit-btn"
              :disabled="!diaryContent.trim() || submitting"
              @click="submitDiary"
            >
              <span v-if="submitting" class="submit-spinner"></span>
              {{ submitting ? '保存中...' : '保存日记' }}
            </button>
          </div>
        </div>
      </section>

      <!-- 历史日记列表 -->
      <section class="diary-history">
        <h2 class="section-heading">📖 历史日记</h2>
        <div v-if="loadingDiaries" class="loading-state">
          <span class="loading-spinner"></span>
          <p>加载中...</p>
        </div>
        <div v-else-if="diaries.length === 0" class="empty-state">
          <span class="empty-icon">📝</span>
          <p>还没有日记，写下第一篇吧</p>
        </div>
        <div v-else class="diary-cards">
          <article v-for="diary in diaries" :key="diary.id" class="diary-card">
            <div class="diary-card-header">
              <div class="diary-card-date">
                <span class="diary-date">{{ formatDate(diary.createTime) }}</span>
                <span class="diary-time">{{ formatTime(diary.createTime) }}</span>
              </div>
              <div class="diary-card-right">
                <span v-if="diary.mood" class="diary-mood" :title="diary.moodLabel">
                  {{ moodEmoji(diary.mood) }}
                </span>
                <button class="diary-delete-btn" title="删除" @click="handleDelete(diary)">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                </button>
              </div>
            </div>
            <p class="diary-card-content">{{ diary.content }}</p>
            <div class="diary-card-tags" v-if="diary.tags && diary.tags.length">
              <span v-for="(tag, i) in diary.tags" :key="i" class="diary-tag">{{ tag }}</span>
            </div>
            <div v-if="diary.aiAnalysis" class="diary-ai-analysis">
              <div class="ai-analysis-header" @click="toggleAnalysis(diary.id)">
                <span class="ai-analysis-title">🤖 AI 情绪分析</span>
                <span class="ai-analysis-arrow">{{ expandedAnalysis[diary.id] ? '▲' : '▼' }}</span>
              </div>
              <div v-show="expandedAnalysis[diary.id]" class="ai-analysis-body">
                <pre class="ai-analysis-text">{{ diary.aiAnalysis }}</pre>
              </div>
            </div>
            <button v-else class="ai-analyze-btn" :disabled="analyzingId === diary.id" @click="handleAnalyze(diary)">
              {{ analyzingId === diary.id ? '分析中...' : '🤖 AI 分析情绪' }}
            </button>
          </article>
        </div>
      </section>

      <!-- 每日情感建议 -->
      <section class="daily-advice">
        <div class="advice-header">
          <p class="page-eyebrow">Daily Advice</p>
          <h2 class="page-title">💝 每日情感建议</h2>
          <p class="page-desc">AI 根据你的情感状态和日记内容，为你生成专属的情感建议</p>
        </div>

        <!-- 加载状态 -->
        <div v-if="loadingAdvice" class="loading-state">
          <span class="loading-spinner"></span>
          <p>加载中...</p>
        </div>

        <!-- 空状态 -->
        <div v-else-if="!todayAdvice" class="empty-state">
          <span class="empty-icon">📝</span>
          <p>今天还没有建议哦</p>
          <button class="advice-gen-btn" :disabled="generatingAdvice" @click="handleGenerateAdvice">
            {{ generatingAdvice ? '生成中...' : '✨ 生成今日建议' }}
          </button>
        </div>

        <!-- 建议卡片 -->
        <div v-else class="advice-card">
          <div class="advice-content">{{ todayAdvice.content }}</div>
          <div class="advice-footer">
            <span class="advice-date">{{ formatDate(todayAdvice.adviceDate || todayAdvice.createTime) }}</span>
            <button class="advice-regen-btn" :disabled="generatingAdvice" @click="handleGenerateAdvice">
              {{ generatingAdvice ? '生成中...' : '重新生成' }}
            </button>
          </div>
          <div v-if="adviceHistory.length" class="advice-history-toggle" @click="showAdviceHistory = !showAdviceHistory">
            <span>{{ showAdviceHistory ? '收起历史' : '查看最近建议' }}</span>
            <span>{{ showAdviceHistory ? '▲' : '▼' }}</span>
          </div>
          <div v-show="showAdviceHistory" class="advice-history-list">
            <div v-for="adv in adviceHistory" :key="adv.id" class="advice-history-item">
              <span class="advice-history-date">{{ formatDate(adv.adviceDate || adv.createTime) }}</span>
              <p class="advice-history-content">{{ adv.content }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 关系状态时间线 -->
      <section class="diary-timeline" v-if="timeline.length">
        <h2 class="section-heading">💕 我的情感历程</h2>
        <div class="timeline-track">
          <div v-for="(log, i) in timeline" :key="i" class="timeline-node">
            <div class="timeline-dot" :class="'dot-' + log.newStatus"></div>
            <div class="timeline-content">
              <span class="timeline-date">{{ formatDate(log.createTime) }}</span>
              <span class="timeline-change">
                <span v-if="log.oldStatus" class="timeline-old">{{ statusLabel(log.oldStatus) }}</span>
                <span class="timeline-arrow-icon">→</span>
                <span class="timeline-new">{{ statusLabel(log.newStatus) }}</span>
              </span>
            </div>
          </div>
        </div>
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useHead } from '@vueuse/head'
import AppHeader from '../components/AppHeader.vue'
import AppFooter from '../components/AppFooter.vue'
import { createDiary, listDiaries, deleteDiary, analyzeDiary, getRelationshipTimeline, getTodayAdvice, getAdviceHistory, generateAdvice } from '../api'

useHead({
  title: '情感日记 - 恋吧AI',
  meta: [
    { name: 'description', content: '记录每天的心情，AI 帮你分析情绪变化，见证你的情感成长。' }
  ]
})

const moodOptions = [
  { value: 1, emoji: '😢', label: '很低落' },
  { value: 2, emoji: '😔', label: '有些难过' },
  { value: 3, emoji: '😐', label: '一般' },
  { value: 4, emoji: '😊', label: '不错' },
  { value: 5, emoji: '🥰', label: '很棒' }
]

const diaryContent = ref('')
const selectedMood = ref(null)
const diaryTags = ref([])
const tagInput = ref('')
const submitting = ref(false)
const diaries = ref([])
const loadingDiaries = ref(true)
const expandedAnalysis = reactive({})
const analyzingId = ref(null)
const timeline = ref([])

// 每日情感建议
const todayAdvice = ref(null)
const adviceHistory = ref([])
const loadingAdvice = ref(true)
const generatingAdvice = ref(false)
const showAdviceHistory = ref(false)

const addTag = () => {
  const tag = tagInput.value.trim()
  if (tag && !diaryTags.value.includes(tag) && diaryTags.value.length < 5) {
    diaryTags.value.push(tag)
    tagInput.value = ''
  }
}

const submitDiary = async () => {
  if (!diaryContent.value.trim() || submitting.value) return
  submitting.value = true
  try {
    const res = await createDiary(diaryContent.value.trim(), selectedMood.value, diaryTags.value.length ? diaryTags.value : null)
    if (res.code === 0 && res.data) {
      diaries.value.unshift(res.data)
      diaryContent.value = ''
      selectedMood.value = null
      diaryTags.value = []
    }
  } catch (e) {
    console.error('保存日记失败:', e)
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (diary) => {
  if (!window.confirm(`确定删除这篇日记吗？`)) return
  try {
    const res = await deleteDiary(diary.id)
    if (res.code === 0) {
      diaries.value = diaries.value.filter(d => d.id !== diary.id)
    }
  } catch (e) {
    console.error('删除日记失败:', e)
  }
}

const handleAnalyze = async (diary) => {
  analyzingId.value = diary.id
  try {
    const res = await analyzeDiary(diary.id)
    if (res.code === 0 && res.data) {
      diary.aiAnalysis = res.data.aiAnalysis
    }
  } catch (e) {
    console.error('AI 分析失败:', e)
  } finally {
    analyzingId.value = null
  }
}

const toggleAnalysis = (id) => {
  expandedAnalysis[id] = !expandedAnalysis[id]
}

const moodEmoji = (mood) => {
  const opt = moodOptions.find(m => m.value === mood)
  return opt ? opt.emoji : ''
}

const statusLabel = (status) => {
  const map = { single: '单身', dating: '恋爱中', married: '已婚' }
  return map[status] || status || '未设置'
}

const handleGenerateAdvice = async () => {
  generatingAdvice.value = true
  try {
    const res = await generateAdvice()
    if (res.code === 0 && res.data) {
      todayAdvice.value = res.data
    }
  } catch (e) {
    console.error('生成建议失败:', e)
  } finally {
    generatingAdvice.value = false
  }
}

const formatDate = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const formatTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(async () => {
  try {
    const [diaryRes, timelineRes, adviceRes, historyRes] = await Promise.all([
      listDiaries(),
      getRelationshipTimeline(),
      getTodayAdvice(),
      getAdviceHistory(7)
    ])
    if (diaryRes.code === 0 && diaryRes.data) {
      diaries.value = diaryRes.data
    }
    if (timelineRes.code === 0 && timelineRes.data) {
      timeline.value = timelineRes.data.reverse()
    }
    if (adviceRes.code === 0) todayAdvice.value = adviceRes.data
    if (historyRes.code === 0 && historyRes.data) adviceHistory.value = historyRes.data
  } catch (e) {
    console.error('加载数据失败:', e)
  } finally {
    loadingDiaries.value = false
    loadingAdvice.value = false
  }
})
</script>

<style scoped>
.diary-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--paper, #faf8f5);
}

.diary-main {
  flex: 1;
  max-width: 720px;
  width: 100%;
  margin: 0 auto;
  padding: 48px 24px 80px;
}

.page-head {
  text-align: center;
  margin-bottom: 36px;
}

.page-eyebrow {
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: #e05575;
  margin-bottom: 8px;
}

.page-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: #2c2520;
  margin-bottom: 8px;
}

.page-desc {
  font-size: 0.92rem;
  color: #888;
}

/* 撰写区 */
.writer-card {
  background: #fff;
  border: 1px solid #f0e4e8;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 36px;
  box-shadow: 0 2px 12px rgba(224, 85, 117, 0.04);
}

.writer-title {
  font-size: 1.05rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 14px;
}

.writer-textarea {
  width: 100%;
  border: 1.5px solid #f0e4e8;
  border-radius: 12px;
  padding: 14px 16px;
  font-size: 0.95rem;
  line-height: 1.7;
  color: #333;
  resize: vertical;
  font-family: inherit;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.writer-textarea:focus {
  outline: none;
  border-color: #ff8fab;
  box-shadow: 0 0 0 3px rgba(255, 107, 139, 0.08);
}

.writer-meta {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.mood-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mood-label {
  font-size: 0.82rem;
  color: #999;
  margin-right: 4px;
}

.mood-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid transparent;
  background: #faf5f7;
  font-size: 1.1rem;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mood-btn:hover {
  background: #fff0f3;
  transform: scale(1.1);
}

.mood-btn.active {
  border-color: #ff6b8b;
  background: #fff0f3;
  box-shadow: 0 2px 8px rgba(255, 107, 139, 0.2);
  transform: scale(1.15);
}

.tag-input-wrap {
  flex: 1;
  min-width: 160px;
}

.tag-input {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid #f0e4e8;
  border-radius: 10px;
  font-size: 0.85rem;
  color: #333;
  box-sizing: border-box;
}

.tag-input:focus {
  outline: none;
  border-color: #ff8fab;
}

.tag-chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #fff0f3;
  color: #e05575;
  font-size: 0.78rem;
  font-weight: 500;
}

.tag-remove {
  background: none;
  border: none;
  color: #e05575;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 0;
  line-height: 1;
}

.writer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.char-count {
  font-size: 0.78rem;
  color: #bbb;
}

.submit-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 28px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #ff6b8b, #ff8fa3);
  color: #fff;
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 4px 16px rgba(255, 107, 139, 0.25);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 107, 139, 0.35);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.submit-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

/* 历史日记 */
.section-heading {
  font-size: 1.1rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 18px;
}

.loading-state, .empty-state {
  text-align: center;
  padding: 40px 0;
  color: #aaa;
}

.loading-spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 3px solid #f0e4e8;
  border-top-color: #e05575;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 8px;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty-icon {
  font-size: 2.4rem;
  display: block;
  margin-bottom: 10px;
}

.diary-cards {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.diary-card {
  background: #fff;
  border: 1px solid #f0e4e8;
  border-radius: 14px;
  padding: 18px 20px;
  transition: box-shadow 0.2s;
}

.diary-card:hover {
  box-shadow: 0 4px 16px rgba(224, 85, 117, 0.06);
}

.diary-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.diary-card-date {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.diary-date {
  font-size: 0.88rem;
  font-weight: 600;
  color: #333;
}

.diary-time {
  font-size: 0.78rem;
  color: #bbb;
}

.diary-card-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.diary-mood {
  font-size: 1.3rem;
}

.diary-delete-btn {
  background: none;
  border: none;
  color: #ccc;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.2s;
}

.diary-delete-btn:hover {
  color: #e05575;
  background: #fff0f3;
}

.diary-card-content {
  font-size: 0.92rem;
  line-height: 1.7;
  color: #444;
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 10px;
}

.diary-card-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.diary-tag {
  padding: 2px 10px;
  border-radius: 999px;
  background: #faf5f7;
  color: #999;
  font-size: 0.75rem;
}

.diary-ai-analysis {
  border-top: 1px dashed #fdeef0;
  padding-top: 10px;
}

.ai-analysis-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  padding: 4px 0;
}

.ai-analysis-title {
  font-size: 0.82rem;
  font-weight: 600;
  color: #e05575;
}

.ai-analysis-arrow {
  font-size: 0.7rem;
  color: #ccc;
}

.ai-analysis-body {
  padding: 8px 0 0;
}

.ai-analysis-text {
  font-size: 0.88rem;
  line-height: 1.7;
  color: #555;
  white-space: pre-wrap;
  font-family: inherit;
  margin: 0;
}

.ai-analyze-btn {
  background: none;
  border: 1px dashed #ffd6e0;
  border-radius: 10px;
  padding: 8px 16px;
  font-size: 0.82rem;
  color: #e05575;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 6px;
}

.ai-analyze-btn:hover:not(:disabled) {
  background: #fff5f7;
  border-color: #ff8fab;
}

.ai-analyze-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 时间线 */
.diary-timeline {
  margin-top: 40px;
}

.timeline-track {
  position: relative;
  padding-left: 24px;
}

.timeline-track::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: linear-gradient(to bottom, #ff8fab, #ffd6e0);
  border-radius: 1px;
}

.timeline-node {
  position: relative;
  padding-bottom: 20px;
}

.timeline-node:last-child {
  padding-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: -20px;
  top: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #ff8fab;
  background: #fff;
}

.dot-single { border-color: #7eb8da; background: #f0f7fc; }
.dot-dating { border-color: #ff8fab; background: #fff0f3; }
.dot-married { border-color: #f5a623; background: #fef8ee; }

.timeline-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.timeline-date {
  font-size: 0.82rem;
  color: #999;
  min-width: 90px;
}

.timeline-change {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.88rem;
}

.timeline-old {
  color: #bbb;
  text-decoration: line-through;
}

.timeline-arrow-icon {
  color: #ff8fab;
  font-size: 0.8rem;
}

.timeline-new {
  font-weight: 600;
  color: #e05575;
}

/* 每日情感建议 */
.daily-advice {
  margin-top: 40px;
}

.advice-header {
  text-align: center;
  margin-bottom: 36px;
}

.advice-header .page-eyebrow {
  color: #e05575;
}

.advice-header .page-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: #2c2520;
  margin-bottom: 8px;
}

.advice-header .page-desc {
  font-size: 0.92rem;
  color: #888;
}

.advice-card {
  background: linear-gradient(135deg, #fff5f7, #fff9fb);
  border: 1px solid #fdeef0;
  border-radius: 14px;
  padding: 24px 28px;
  margin-bottom: 20px;
}

.advice-content {
  font-size: 0.95rem;
  line-height: 1.7;
  color: #444;
  margin-bottom: 16px;
  white-space: pre-wrap;
}

.advice-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px dashed #fdeef0;
}

.advice-date {
  font-size: 0.78rem;
  color: #bbb;
}

.advice-regen-btn {
  padding: 5px 14px;
  border-radius: 999px;
  border: 1px solid #ffd6e0;
  background: #fff;
  font-size: 0.78rem;
  color: #e05575;
  cursor: pointer;
  transition: all 0.2s;
}

.advice-regen-btn:hover:not(:disabled) {
  background: #fff0f3;
  border-color: #ff8fab;
}

.advice-regen-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.advice-history-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed #fdeef0;
  font-size: 0.82rem;
  color: #999;
  cursor: pointer;
}

.advice-history-toggle:hover {
  color: #e05575;
}

.advice-history-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.advice-history-item {
  padding: 10px 14px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #fdeef0;
}

.advice-history-date {
  font-size: 0.75rem;
  color: #bbb;
  display: block;
  margin-bottom: 4px;
}

.advice-history-content {
  font-size: 0.85rem;
  line-height: 1.6;
  color: #666;
  margin: 0;
}

.advice-gen-btn {
  margin-top: 12px;
  padding: 10px 24px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #ff6b8b, #ff8fa3);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 4px 16px rgba(255, 107, 139, 0.2);
}

.advice-gen-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 107, 139, 0.3);
}

.advice-gen-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 600px) {
  .diary-main {
    padding: 32px 14px 60px;
  }
  .page-title {
    font-size: 1.4rem;
  }
  .writer-meta {
    flex-direction: column;
    gap: 12px;
  }
  .writer-card {
    padding: 18px 16px;
  }
}
</style>
