<template>
  <div class="community-page">
    <AppHeader />

    <main class="community-main">
      <div class="community-header">
        <h1 class="community-title">情感社区</h1>
        <p class="community-subtitle">匿名倾诉 · 温暖互助 · 每一个故事都值得被听见</p>
      </div>

      <!-- 标签筛选 -->
      <div class="tag-filter">
        <button
          v-for="t in tagOptions"
          :key="t.value"
          class="tag-chip"
          :class="{ active: currentTag === t.value }"
          @click="switchTag(t.value)"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="community-loading">
        <span class="spinner"></span> 加载中...
      </div>

      <!-- 空状态 -->
      <div v-else-if="posts.length === 0" class="community-empty">
        <p class="empty-icon">🌸</p>
        <p>还没有帖子，快来分享你的情感故事吧～</p>
      </div>

      <!-- 帖子列表 -->
      <div v-else class="post-list">
        <div v-for="post in posts" :key="post.id" class="post-card" @click="openDetail(post)">
          <div class="post-header">
            <span class="post-avatar">{{ avatarChar(post) }}</span>
            <div class="post-meta">
              <span class="post-nick">{{ post.nickname || '匿名用户' }}</span>
              <span class="post-time">{{ formatDate(post.createTime) }}</span>
            </div>
            <span v-if="post.tag" class="post-tag" :class="'tag-' + tagClass(post.tag)">{{ post.tag }}</span>
          </div>
          <p class="post-content">{{ post.content }}</p>
          <div class="post-footer">
            <button class="action-btn" :class="{ liked: likedSet.has(post.id) }" @click.stop="handleLike(post)">
              {{ likedSet.has(post.id) ? '❤️' : '🤍' }} {{ post.likeCount || 0 }}
            </button>
            <span class="action-btn">💬 {{ post.commentCount || 0 }}</span>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="totalPages > 1" class="pagination">
          <button class="page-btn" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
          <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
          <button class="page-btn" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">下一页</button>
        </div>
      </div>

      <!-- 浮动发帖按钮 -->
      <button class="fab-btn" @click="openCreate">✍ 发帖</button>

      <!-- 发帖弹窗 -->
      <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
        <div class="modal-box">
          <h2 class="modal-title">发布帖子</h2>
          <div class="form-field">
            <label class="form-label">标签</label>
            <div class="create-tag-row">
              <button
                v-for="t in createTagOptions"
                :key="t"
                class="tag-chip"
                :class="{ active: createForm.tag === t }"
                @click="createForm.tag = createForm.tag === t ? '' : t"
              >
                {{ t }}
              </button>
            </div>
          </div>
          <div class="form-field">
            <label class="form-label">匿名设置</label>
            <div class="anonymous-row">
              <label class="anonymous-check">
                <input v-model="createForm.anonymous" type="checkbox" />
                匿名发布
              </label>
              <input
                v-if="!createForm.anonymous"
                v-model.trim="createForm.nickname"
                class="nick-input"
                maxlength="20"
                placeholder="社区昵称（默认显示你的用户名）"
              />
            </div>
          </div>
          <div class="form-field">
            <label class="form-label">内容</label>
            <textarea
              v-model.trim="createForm.content"
              class="create-textarea"
              rows="6"
              maxlength="2000"
              placeholder="分享你的情感故事、困惑或喜悦...（2000 字以内）"
            ></textarea>
            <p class="char-count">{{ createForm.content.length }} / 2000</p>
          </div>
          <div class="modal-actions">
            <button class="btn-cancel" @click="showCreateModal = false">取消</button>
            <button class="btn-submit" :disabled="submitting || !createForm.content" @click="submitPost">
              {{ submitting ? '发布中...' : '发布' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 帖子详情弹窗 -->
      <div v-if="showDetailModal" class="modal-overlay" @click.self="closeDetail">
        <div class="modal-box detail-box">
          <div v-if="loadingDetail" class="community-loading">
            <span class="spinner"></span> 加载中...
          </div>
          <template v-else-if="detailPost">
            <div class="post-header">
              <span class="post-avatar">{{ avatarChar(detailPost) }}</span>
              <div class="post-meta">
                <span class="post-nick">{{ detailPost.nickname || '匿名用户' }}</span>
                <span class="post-time">{{ formatDate(detailPost.createTime) }}</span>
              </div>
              <span v-if="detailPost.tag" class="post-tag">{{ detailPost.tag }}</span>
            </div>
            <p class="detail-content">{{ detailPost.content }}</p>
            <div class="post-footer">
              <button class="action-btn" :class="{ liked: likedSet.has(detailPost.id) }" @click="handleLike(detailPost)">
                {{ likedSet.has(detailPost.id) ? '❤️' : '🤍' }} {{ detailPost.likeCount || 0 }}
              </button>
            </div>

            <!-- 评论区 -->
            <div class="comment-section">
              <h3 class="comment-title">评论（{{ detailComments.length }}）</h3>
              <div v-if="detailComments.length === 0" class="comment-empty">暂无评论，来抢沙发～</div>
              <div v-for="c in detailComments" :key="c.id" class="comment-item">
                <span class="comment-avatar">{{ avatarChar(c) }}</span>
                <div class="comment-body">
                  <div class="comment-meta">
                    <span class="post-nick">{{ c.nickname || '匿名用户' }}</span>
                    <span class="post-time">{{ formatDate(c.createTime) }}</span>
                  </div>
                  <p class="comment-content">{{ c.content }}</p>
                </div>
              </div>
              <!-- 评论输入 -->
              <div class="comment-input-row">
                <input
                  v-model.trim="commentContent"
                  class="comment-input"
                  maxlength="500"
                  placeholder="写下你的想法..."
                  @keyup.enter="submitComment"
                />
                <button class="btn-submit comment-submit" :disabled="submitting || !commentContent" @click="submitComment">
                  发送
                </button>
              </div>
            </div>
          </template>
        </div>
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
import { useAuth } from '../composables/useAuth'
import { listPosts, createPost, getPost, createComment, likePost } from '../api'

useHead({
  title: '情感社区 - 恋吧AI',
  meta: [
    {
      name: 'description',
      content: '恋吧AI情感社区：匿名倾诉情感故事、分享恋爱喜悦、寻求温暖互助。'
    }
  ]
})

const { loginUser } = useAuth()

// 标签筛选
const tagOptions = [
  { label: '全部', value: '' },
  { label: '倾诉', value: '倾诉' },
  { label: '求助', value: '求助' },
  { label: '分享', value: '分享' },
  { label: '讨论', value: '讨论' }
]
const createTagOptions = ['倾诉', '求助', '分享', '讨论']
const currentTag = ref('')

// 列表状态
const posts = ref([])
const loading = ref(true)
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = 20

// 点赞记录（本次会话内）
const likedSet = ref(new Set())

// 发帖表单
const showCreateModal = ref(false)
const submitting = ref(false)
const createForm = reactive({
  content: '',
  tag: '',
  nickname: '',
  anonymous: true
})

// 详情弹窗
const showDetailModal = ref(false)
const loadingDetail = ref(false)
const detailPost = ref(null)
const detailComments = ref([])
const commentContent = ref('')

const formatDate = (ts) => {
  if (!ts) return '—'
  const d = new Date(ts)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const avatarChar = (item) => {
  const name = item?.nickname || '匿'
  return name.charAt(0).toUpperCase()
}

const tagClass = (tag) => {
  return { '倾诉': 'talk', '求助': 'help', '分享': 'share', '讨论': 'discuss' }[tag] || 'talk'
}

// 加载帖子列表
const loadPosts = async () => {
  loading.value = true
  try {
    const res = await listPosts(currentTag.value, currentPage.value, pageSize)
    if (res.code === 0 && res.data) {
      posts.value = res.data.records || []
      totalPages.value = Math.max(1, res.data.pages || 1)
    }
  } catch (e) {
    console.error('加载帖子失败:', e)
  } finally {
    loading.value = false
  }
}

const switchTag = (tag) => {
  currentTag.value = tag
  currentPage.value = 1
  loadPosts()
}

const changePage = (page) => {
  currentPage.value = page
  loadPosts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 发帖
const openCreate = () => {
  createForm.content = ''
  createForm.tag = ''
  createForm.nickname = ''
  createForm.anonymous = true
  showCreateModal.value = true
}

const submitPost = async () => {
  if (!createForm.content) return
  submitting.value = true
  try {
    // 匿名时不传昵称；非匿名时未填昵称则用登录用户名
    const nickname = createForm.anonymous
      ? ''
      : (createForm.nickname || loginUser.value?.userName || '')
    const res = await createPost(createForm.content, createForm.tag || null, nickname)
    if (res.code === 0) {
      showCreateModal.value = false
      currentPage.value = 1
      loadPosts()
    } else {
      alert(res.message || '发布失败')
    }
  } catch (e) {
    console.error('发帖失败:', e)
    alert('发布失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

// 帖子详情
const openDetail = async (post) => {
  showDetailModal.value = true
  loadingDetail.value = true
  detailPost.value = null
  detailComments.value = []
  try {
    const res = await getPost(post.id)
    if (res.code === 0 && res.data) {
      detailPost.value = res.data.post
      detailComments.value = res.data.comments || []
    }
  } catch (e) {
    console.error('加载帖子详情失败:', e)
  } finally {
    loadingDetail.value = false
  }
}

const closeDetail = () => {
  showDetailModal.value = false
  detailPost.value = null
  detailComments.value = []
  commentContent.value = ''
}

// 点赞
const handleLike = async (post) => {
  if (likedSet.value.has(post.id)) return
  try {
    const res = await likePost(post.id)
    if (res.code === 0) {
      likedSet.value.add(post.id)
      post.likeCount = (post.likeCount || 0) + 1
    }
  } catch (e) {
    console.error('点赞失败:', e)
  }
}

// 评论
const submitComment = async () => {
  if (!commentContent.value || !detailPost.value) return
  submitting.value = true
  try {
    const res = await createComment(detailPost.value.id, commentContent.value, loginUser.value?.userName || '')
    if (res.code === 0) {
      commentContent.value = ''
      // 刷新评论列表与帖子评论数
      const detailRes = await getPost(detailPost.value.id)
      if (detailRes.code === 0 && detailRes.data) {
        detailPost.value = detailRes.data.post
        detailComments.value = detailRes.data.comments || []
      }
      // 同步列表中的评论数
      const inList = posts.value.find(p => p.id === detailPost.value.id)
      if (inList) inList.commentCount = detailPost.value.commentCount
    }
  } catch (e) {
    console.error('评论失败:', e)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadPosts()
})
</script>

<style scoped>
.community-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--paper);
}

.community-main {
  flex: 1;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  padding: 56px 24px 120px;
}

.community-header {
  text-align: center;
  margin-bottom: 30px;
}

.community-title {
  font-family: var(--font-display);
  font-size: 1.9rem;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 8px;
}

.community-subtitle {
  font-size: 0.9rem;
  color: var(--ink-faint);
}

/* 标签筛选 */
.tag-filter {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 28px;
}

.tag-chip {
  padding: 8px 20px;
  border-radius: 999px;
  border: 1.5px solid var(--line);
  background: var(--white);
  font-size: 0.88rem;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.2s;
}

.tag-chip:hover {
  border-color: var(--rose);
  color: var(--rose-deep);
}

.tag-chip.active {
  background: var(--grad-rose);
  color: var(--white);
  border-color: transparent;
  box-shadow: var(--shadow-rose);
}

/* 加载与空状态 */
.community-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--ink-faint);
  font-size: 0.9rem;
}

.spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(224, 90, 114, 0.2);
  border-top-color: var(--rose);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.community-empty {
  text-align: center;
  padding: 60px 0;
  color: var(--ink-faint);
  font-size: 0.95rem;
}

.empty-icon {
  font-size: 2.4rem;
  margin-bottom: 12px;
}

/* 帖子卡片 */
.post-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.post-card {
  background: var(--white);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 22px 24px;
  cursor: pointer;
  transition: all 0.2s;
}

.post-card:hover {
  border-color: rgba(224, 90, 114, 0.4);
  box-shadow: var(--shadow-soft);
  transform: translateY(-2px);
}

.post-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.post-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--grad-rose);
  color: var(--white);
  font-size: 1rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.post-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.post-nick {
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--ink);
}

.post-time {
  font-size: 0.75rem;
  color: var(--ink-faint);
}

.post-tag {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 999px;
  flex-shrink: 0;
}

.tag-talk {
  background: rgba(224, 90, 114, 0.1);
  color: var(--rose-deep);
}

.tag-help {
  background: rgba(255, 159, 67, 0.12);
  color: #d97706;
}

.tag-share {
  background: rgba(46, 125, 116, 0.1);
  color: var(--sage);
}

.tag-discuss {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}

.post-content {
  font-size: 0.95rem;
  line-height: 1.7;
  color: var(--ink-soft);
  margin-bottom: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
}

.post-footer {
  display: flex;
  gap: 16px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  font-size: 0.85rem;
  color: var(--ink-faint);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: var(--grad-soft);
  color: var(--rose-deep);
}

.action-btn.liked {
  color: var(--rose-deep);
  font-weight: 600;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-top: 28px;
}

.page-btn {
  padding: 8px 20px;
  border-radius: 999px;
  border: 1.5px solid var(--line);
  background: var(--white);
  font-size: 0.85rem;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--rose);
  color: var(--rose-deep);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 0.85rem;
  color: var(--ink-faint);
}

/* 浮动发帖按钮 */
.fab-btn {
  position: fixed;
  right: 36px;
  bottom: 42px;
  padding: 14px 28px;
  border: none;
  border-radius: 999px;
  background: var(--grad-rose);
  color: var(--white);
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-rose);
  transition: all 0.2s;
  z-index: 50;
}

.fab-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 32px rgba(224, 90, 114, 0.35);
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(40, 30, 35, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 24px;
}

.modal-box {
  background: var(--white);
  border-radius: var(--radius-lg);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.2);
  padding: 28px 30px;
  width: 100%;
  max-width: 560px;
  max-height: 86vh;
  overflow-y: auto;
}

.modal-title {
  font-family: var(--font-display);
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 20px;
}

.form-field {
  margin-bottom: 18px;
}

.form-label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--ink-soft);
  margin-bottom: 8px;
}

.create-tag-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.anonymous-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.anonymous-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.88rem;
  color: var(--ink-soft);
  cursor: pointer;
  flex-shrink: 0;
}

.nick-input {
  flex: 1;
  padding: 9px 14px;
  border: 1.5px solid var(--line);
  border-radius: var(--radius-sm);
  font-size: 0.9rem;
  color: var(--ink);
  background: var(--paper);
}

.nick-input:focus {
  outline: none;
  border-color: var(--rose);
  background: var(--white);
}

.create-textarea {
  width: 100%;
  padding: 14px 16px;
  border: 1.5px solid var(--line);
  border-radius: var(--radius-md);
  font-size: 0.95rem;
  line-height: 1.7;
  color: var(--ink);
  background: var(--paper);
  resize: vertical;
  font-family: inherit;
  box-sizing: border-box;
}

.create-textarea:focus {
  outline: none;
  border-color: var(--rose);
  background: var(--white);
}

.char-count {
  text-align: right;
  font-size: 0.75rem;
  color: var(--ink-faint);
  margin-top: 6px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.btn-cancel {
  padding: 10px 24px;
  border-radius: 999px;
  border: 1.5px solid var(--line);
  background: var(--white);
  font-size: 0.9rem;
  color: var(--ink-soft);
  cursor: pointer;
}

.btn-cancel:hover {
  border-color: var(--ink-faint);
}

.btn-submit {
  padding: 10px 28px;
  border: none;
  border-radius: 999px;
  background: var(--grad-rose);
  color: var(--white);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-rose);
  transition: all 0.2s;
}

.btn-submit:hover:not(:disabled) {
  transform: translateY(-1px);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 详情弹窗 */
.detail-box {
  max-width: 640px;
}

.detail-content {
  font-size: 1rem;
  line-height: 1.85;
  color: var(--ink);
  margin-bottom: 16px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 评论区 */
.comment-section {
  margin-top: 20px;
  border-top: 1px dashed var(--line);
  padding-top: 18px;
}

.comment-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 14px;
}

.comment-empty {
  font-size: 0.85rem;
  color: var(--ink-faint);
  text-align: center;
  padding: 16px 0;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(232, 224, 212, 0.6);
}

.comment-item:last-of-type {
  border-bottom: none;
}

.comment-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--grad-soft);
  border: 1px solid var(--line);
  color: var(--rose-deep);
  font-size: 0.85rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 4px;
}

.comment-content {
  font-size: 0.9rem;
  line-height: 1.65;
  color: var(--ink-soft);
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-input-row {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.comment-input {
  flex: 1;
  padding: 10px 16px;
  border: 1.5px solid var(--line);
  border-radius: 999px;
  font-size: 0.9rem;
  color: var(--ink);
  background: var(--paper);
}

.comment-input:focus {
  outline: none;
  border-color: var(--rose);
  background: var(--white);
}

.comment-submit {
  padding: 10px 22px;
  flex-shrink: 0;
}

/* 响应式 */
@media (max-width: 640px) {
  .community-main {
    padding: 36px 14px 110px;
  }

  .fab-btn {
    right: 18px;
    bottom: 26px;
    padding: 12px 22px;
  }

  .modal-box {
    padding: 22px 18px;
  }

  .anonymous-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
