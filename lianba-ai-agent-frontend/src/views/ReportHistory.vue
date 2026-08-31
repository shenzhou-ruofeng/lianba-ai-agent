<template>
  <div class="reports-page">
    <AppHeader />

    <main class="reports-main">
      <div class="page-head">
        <p class="page-eyebrow">Love Reports</p>
        <h1 class="page-title">恋爱报告历史</h1>
        <p class="page-desc">查看 AI 为你生成的所有恋爱报告，支持多格式下载存档</p>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <span class="loading-spinner"></span>
        <p>加载报告中...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="reports.length === 0" class="empty-state">
        <span class="empty-icon" aria-hidden="true">💌</span>
        <p class="empty-title">还没有恋爱报告</p>
        <p class="empty-hint">与 AI 恋爱大师多聊几轮后，点击「报告」按钮即可生成专属恋爱报告</p>
        <router-link to="/love-master" class="cta cta-primary">去和恋爱大师聊聊</router-link>
      </div>

      <!-- 报告列表 -->
      <div v-else class="report-cards">
        <article
          v-for="report in reports"
          :key="report.id"
          class="report-card"
          :class="{ expanded: expandedId === report.id }"
        >
          <div class="report-card-header" @click="toggleExpand(report.id)">
            <div class="report-card-info">
              <h3 class="report-card-title">💌 {{ report.title }}</h3>
              <p class="report-card-time">{{ formatDate(report.createTime) }}</p>
            </div>
            <div class="report-card-actions">
              <button
                v-for="fmt in formats"
                :key="fmt.value"
                class="report-dl-btn"
                :disabled="downloading[report.id + '-' + fmt.value]"
                @click.stop="downloadReport(report, fmt.value)"
              >
                <span v-if="downloading[report.id + '-' + fmt.value]" class="dl-spinner"></span>
                <span v-else>{{ fmt.label }}</span>
              </button>
              <span class="expand-arrow">{{ expandedId === report.id ? '▲' : '▼' }}</span>
            </div>
          </div>
          <div v-if="expandedId === report.id" class="report-card-body">
            <ol class="report-suggestions">
              <li v-for="(sug, idx) in report.suggestions" :key="idx">{{ sug }}</li>
            </ol>
          </div>
        </article>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useHead } from '@vueuse/head'
import AppHeader from '../components/AppHeader.vue'
import AppFooter from '../components/AppFooter.vue'
import { listLoveReports, exportLoveReport } from '../api'

useHead({
  title: '恋爱报告历史 - 恋吧AI',
  meta: [
    { name: 'description', content: '查看 AI 为你生成的恋爱报告，回顾情感建议，支持 PDF / Word / Markdown 下载。' }
  ]
})

const reports = ref([])
const loading = ref(true)
const expandedId = ref(null)
const downloading = reactive({})

const formats = [
  { value: 'pdf', label: 'PDF' },
  { value: 'word', label: 'Word' },
  { value: 'md', label: 'MD' }
]

const toggleExpand = (id) => {
  expandedId.value = expandedId.value === id ? null : id
}

const formatDate = (ts) => {
  if (!ts) return '—'
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const downloadReport = async (report, format) => {
  const key = report.id + '-' + format
  if (downloading[key]) return
  downloading[key] = true
  try {
    await exportLoveReport(report, format)
  } catch (e) {
    console.error('下载报告失败:', e)
  } finally {
    downloading[key] = false
  }
}

onMounted(async () => {
  try {
    const res = await listLoveReports()
    if (res.code === 0 && res.data) {
      reports.value = res.data
    }
  } catch (e) {
    console.error('加载报告列表失败:', e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.reports-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--paper, #faf8f5);
}

.reports-main {
  flex: 1;
  max-width: 800px;
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

.loading-state {
  text-align: center;
  padding: 60px 0;
  color: #aaa;
}

.loading-spinner {
  display: inline-block;
  width: 24px;
  height: 24px;
  border: 3px solid #f0e4e8;
  border-top-color: #e05575;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 12px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.empty-icon {
  font-size: 3rem;
  display: block;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #555;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 0.88rem;
  color: #999;
  margin-bottom: 24px;
}

.cta {
  display: inline-block;
  padding: 10px 24px;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s;
}

.cta-primary {
  background: linear-gradient(135deg, #ff6b8b, #ff8fa3);
  color: #fff;
  box-shadow: 0 4px 16px rgba(255, 107, 139, 0.3);
}

.cta-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 107, 139, 0.4);
}

/* 报告卡片列表 */
.report-cards {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.report-card {
  background: #fff;
  border: 1px solid #f0e4e8;
  border-radius: 14px;
  overflow: hidden;
  transition: box-shadow 0.2s;
}

.report-card:hover {
  box-shadow: 0 4px 16px rgba(224, 85, 117, 0.08);
}

.report-card.expanded {
  border-color: #ffd6e0;
}

.report-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  cursor: pointer;
  gap: 12px;
}

.report-card-title {
  font-size: 1rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.report-card-time {
  font-size: 0.78rem;
  color: #aaa;
}

.report-card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.report-dl-btn {
  padding: 4px 12px;
  border-radius: 12px;
  border: 1px solid #ffd6e0;
  background: #fff;
  font-size: 12px;
  color: #e05575;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 40px;
  text-align: center;
}

.report-dl-btn:hover:not(:disabled) {
  background: #fff5f7;
  border-color: #ff8fab;
}

.report-dl-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.dl-spinner {
  display: inline-block;
  width: 10px;
  height: 10px;
  border: 2px solid #ffd6e0;
  border-top-color: #e05575;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.expand-arrow {
  font-size: 10px;
  color: #ccc;
  margin-left: 4px;
}

.report-card-body {
  padding: 0 20px 18px;
  border-top: 1px dashed #fdeef0;
}

.report-suggestions {
  margin: 14px 0 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.report-suggestions li {
  font-size: 0.9rem;
  line-height: 1.6;
  color: #555;
}

@media (max-width: 600px) {
  .reports-main {
    padding: 32px 14px 60px;
  }
  .page-title {
    font-size: 1.4rem;
  }
  .report-card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .report-card-actions {
    align-self: flex-end;
  }
}
</style>
