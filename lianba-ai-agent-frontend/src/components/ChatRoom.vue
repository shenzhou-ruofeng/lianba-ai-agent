<template>
  <div class="chat-container">
    <!-- 恋爱大师模式切换：置顶 tab 栏（豆包风格） -->
    <div v-if="aiType === 'love'" class="chat-mode-bar">
      <button
        v-for="mode in chatModes"
        :key="mode.value"
        class="chat-mode-chip"
        :class="{ active: chatMode === mode.value }"
        :disabled="connectionStatus === 'connecting'"
        @click="switchChatMode(mode.value)"
      >{{ mode.label }}</button>
      <!-- 对象推荐模式下的期望性别硬过滤选项 -->
      <template v-if="chatMode === 'match'">
        <span class="gender-filter-divider"></span>
        <span class="gender-filter-label">只看</span>
        <button
          v-for="g in matchGenders"
          :key="g.value"
          class="chat-mode-chip gender-chip"
          :class="{ active: matchGender === g.value }"
          :disabled="connectionStatus === 'connecting'"
          @click="switchMatchGender(g.value)"
        >{{ g.label }}</button>
      </template>
    </div>

    <!-- 聊天记录区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
        <!-- 工具调用步骤（可折叠） -->
        <div v-if="msg.type === 'tool_step'" class="tool-step-wrapper">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="tool-step-card">
            <div class="tool-step-header" @click="toggleStep(index)">
              <span class="tool-step-arrow">{{ expandedSteps[index] ? '▼' : '▶' }}</span>
              <span class="tool-step-title">Step {{ msg.step }} - 工具调用</span>
              <span class="tool-step-badge">{{ msg.toolCalls?.length || 0 }} 个工具</span>
            </div>
            <div v-if="expandedSteps[index]" class="tool-step-body">
              <div v-for="(tc, tcIdx) in msg.toolCalls" :key="tcIdx" class="tool-call-item">
                <div class="tool-call-header" @click="toggleToolCall(index, tcIdx)">
                  <span class="tool-call-arrow">{{ expandedToolCalls[index + '-' + tcIdx] ? '▼' : '▶' }}</span>
                  <span class="tool-call-name">🔧 {{ tc.toolName }}</span>
                  <span v-if="tc.result" class="tool-call-done">✓</span>
                  <span v-else class="tool-call-spinner">⟳</span>
                </div>
                <div v-if="expandedToolCalls[index + '-' + tcIdx]" class="tool-call-detail">
                  <div class="tool-call-section">
                    <div class="tool-call-label">参数：</div>
                    <pre class="tool-call-value">{{ tc.arguments }}</pre>
                  </div>
                  <div class="tool-call-section" v-if="tc.result">
                    <div class="tool-call-label">结果：</div>
                    <pre class="tool-call-value">{{ tc.result }}</pre>
                  </div>
                  <div class="tool-call-section" v-else>
                    <div class="tool-call-label">结果：</div>
                    <pre class="tool-call-value tool-call-pending">执行中...</pre>
                  </div>
                  <!-- PDF 下载按钮 -->
                  <div v-if="tc.toolName === 'generatePDF' && tc.result && tc.result.includes('PDF generated successfully')" class="tool-call-download">
                    <a :href="getDownloadUrl(tc.result)" class="pdf-download-btn" target="_blank" rel="noopener">
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                      下载 PDF 文件
                    </a>
                  </div>
                  <!-- writeFile 文件下载按钮 -->
                  <div v-if="tc.toolName === 'writeFile' && tc.result && tc.result.includes('File written successfully')" class="tool-call-download">
                    <a :href="getFileDownloadUrl(tc.result)" class="pdf-download-btn" target="_blank" rel="noopener">
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                      下载生成的文件
                    </a>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 思考过程（DeepSeek 风格，可折叠） -->
        <div v-else-if="msg.type === 'thinking'" class="think-wrapper">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="think-card">
            <div class="think-header" @click="toggleThink(index)">
              <span class="think-icon">💭</span>
              <span class="think-title">已深度思考<span v-if="msg.step" class="think-step"> · Step {{ msg.step }}</span></span>
              <span class="think-arrow">{{ expandedThinks[index] === false ? '▶' : '▼' }}</span>
            </div>
            <div v-show="expandedThinks[index] !== false" class="think-body">{{ msg.content }}</div>
          </div>
        </div>

        <!-- 生成的可下载文件列表 -->
        <div v-else-if="msg.type === 'file-list'" class="file-list-wrapper">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="file-list-card">
            <div class="file-list-title">📎 本次生成的文件（可下载）</div>
            <div class="file-list-items">
              <a v-for="(f, fi) in msg.files" :key="fi" :href="buildFileUrl(f.url)" class="pdf-download-btn file-list-btn" target="_blank" rel="noopener">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                {{ f.name }}
              </a>
            </div>
          </div>
        </div>
        
        <!-- 生成的图片预览 + 下载（支持原图 vs 生成图对比） -->
        <div v-else-if="msg.type === 'generated_image'" class="generated-image-wrapper">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <!-- 对比模式：找到了前序用户消息中的引用图片 -->
          <div v-if="findOriginalImage(index)" class="image-compare-card">
            <div class="image-compare-title">🖼️ 原图 vs AI 生成</div>
            <div class="image-compare-grid">
              <div class="image-compare-side">
                <span class="image-compare-label">原图</span>
                <img :src="resolveImageUrl(findOriginalImage(index))" class="image-compare-img" alt="原图" @error="onImageLoadError" />
              </div>
              <div class="image-compare-side">
                <span class="image-compare-label result-label">AI 生成</span>
                <img :src="resolveImageUrl(msg.imageUrl)" class="image-compare-img" alt="AI生成" @error="onImageLoadError" />
              </div>
            </div>
            <div class="image-compare-actions">
              <a :href="resolveDownloadUrl(msg.imageUrl)" class="generated-image-download-btn" target="_blank" rel="noopener">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                下载生成图片
              </a>
            </div>
          </div>
          <!-- 普通模式：无原图对比 -->
          <div v-else class="generated-image-card">
            <div class="generated-image-title">🖼️ AI 生成的图片</div>
            <img :src="resolveImageUrl(msg.imageUrl)" class="generated-image-preview" alt="AI生成的图片" @error="onImageLoadError" @load="e => e.target.style.display=''" />
            <a :href="resolveDownloadUrl(msg.imageUrl)" class="generated-image-download-btn" target="_blank" rel="noopener">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              下载图片
            </a>
          </div>
        </div>

        <!-- 恋爱报告卡片（结构化输出） -->
        <div v-else-if="msg.type === 'love_report'" class="love-report-wrapper">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="love-report-card">
            <div class="love-report-title">💌 {{ msg.report?.title || '恋爱报告' }}</div>
            <ol class="love-report-list">
              <li v-for="(sug, sIdx) in (msg.report?.suggestions || [])" :key="sIdx" class="love-report-item">{{ sug }}</li>
            </ol>
            <!-- 报告下载：PDF / Word / Markdown 三种格式 -->
            <div class="love-report-downloads">
              <span class="love-report-download-label">下载报告：</span>
              <button
                v-for="fmt in reportFormats"
                :key="fmt.value"
                class="love-report-download-btn"
                :disabled="downloadingReports[index + '-' + fmt.value]"
                @click="downloadReport(msg.report, fmt.value, index)"
              >
                <span v-if="downloadingReports[index + '-' + fmt.value]" class="attach-spinner attach-spinner-small"></span>
                <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                {{ fmt.label }}
              </button>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <!-- AI 向用户提问（askHuman 交互式执行） -->
        <div v-else-if="msg.type === 'ask_human'" class="ask-human-wrapper">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="ask-human-card">
            <div class="ask-human-title">🙋 AI 需要你的帮助</div>
            <div class="ask-human-question">{{ msg.content }}</div>
            <!-- 未回复：展示回复输入框 -->
            <div v-if="!msg.answered" class="ask-human-reply">
              <textarea
                v-model="humanReplyDrafts[index]"
                class="ask-human-input"
                placeholder="请输入你的回复..."
                rows="2"
                @keydown.enter.exact.prevent="submitHumanReply(msg, index)"
              ></textarea>
              <button
                class="ask-human-submit-btn"
                :disabled="!((humanReplyDrafts[index] || '').trim()) || humanReplySending[index]"
                @click="submitHumanReply(msg, index)"
              >
                <span v-if="humanReplySending[index]" class="attach-spinner attach-spinner-small"></span>
                <template v-else>回复</template>
              </button>
            </div>
            <!-- 已回复：展示回复内容 -->
            <div v-else class="ask-human-answered">✅ 已回复：{{ msg.answer }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <!-- 最终回答（高亮总结卡片，区别于思考/普通气泡） -->
        <div v-else-if="msg.type === 'ai-summary'" class="summary-wrapper">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="summary-card">
            <div class="summary-header"><span class="summary-badge">✨ 最终回答</span></div>
            <div class="summary-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <!-- 已手动停止提示 -->
        <div v-else-if="msg.type === 'ai-stopped'" class="stopped-wrapper">
          <div class="stopped-card">
            <span class="stopped-icon">⏹</span>
            <span class="stopped-text">{{ msg.content || '回复已被手动停止' }}</span>
          </div>
        </div>

        <!-- AI消息 -->
        <div v-else-if="!msg.isUser" 
             class="message ai-message" 
             :class="[msg.type]">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="message-bubble">
            <div class="message-content">
              {{ msg.content }}
              <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing-indicator">▋</span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>
        
        <!-- 用户消息 -->
        <div v-else class="message user-message" :class="[msg.type]">
          <div class="message-bubble">
            <!-- 引用的附件（微信引用样式：图片/文档在上，提示词在下） -->
            <div v-if="msg.quote" class="quote-block">
              <div v-if="msg.quote.images && msg.quote.images.length" class="quote-images">
                <img
                  v-for="(qi, qIdx) in msg.quote.images"
                  :key="'qi' + qIdx"
                  :src="resolveImageUrl(qi.url) !== '#' ? resolveImageUrl(qi.url) : qi.previewUrl"
                  :alt="qi.fileName || '图片'"
                  class="quote-image"
                  @error="e => e.target.style.display='none'"
                />
              </div>
              <div v-for="(qd, qdIdx) in (msg.quote.docs || [])" :key="'qd' + qdIdx" class="quote-doc">
                📄 {{ qd.fileName }}<span v-if="qd.chunks" class="quote-doc-chunks"> · {{ qd.chunks }} 个片段</span>
                <span :class="['quote-doc-badge', qd.imported ? 'quote-doc-imported' : 'quote-doc-skipped']">{{ qd.imported ? '已导入知识库' : '未入库' }}</span>
              </div>
            </div>
            <!-- 图片消息（旧格式兼容） -->
            <div v-if="msg.type === 'user-images'" class="message-content">
              <div v-if="msg.content" class="user-image-label">{{ msg.content }}</div>
              <img
                v-for="(img, iIdx) in msg.images"
                :key="iIdx"
                :src="img.url || img.previewUrl"
                :alt="img.fileName || '图片'"
                class="user-image-in-chat"
                @error="e => e.target.style.display='none'"
              />
            </div>
            <div v-else-if="msg.content" class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <div class="avatar-placeholder">我</div>
          </div>
        </div>
      </div>

      <!-- 实时状态提示条：浮在AI最新消息气泡右侧 -->
      <div v-if="statusText && connectionStatus === 'connecting'" class="status-bar-inline">
        <span class="status-spinner"></span>
        <span class="status-text">{{ statusText }}</span>
      </div>
    </div>

    <!-- 能力引导气泡（首次对话后展示，引导用户体验工具能力） -->
    <div v-if="showCapabilityGuide && connectionStatus !== 'connecting'" class="capability-guide-bar">
      <span class="guide-label">试试这些：</span>
      <button
        v-for="guide in capabilityGuides"
        :key="guide.label"
        class="guide-chip"
        @click="clickCapabilityGuide(guide)"
      >{{ guide.label }}</button>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-container">
      <div v-if="uploadStatus" class="upload-status" :class="uploadStatus.type">
        {{ uploadStatus.text }}
      </div>
      <!-- 待上传附件区域（图片/文档解析状态） -->
      <div v-if="imagePreviews.length > 0 || docPreviews.length > 0" class="image-previews">
        <div v-for="(img, idx) in imagePreviews" :key="'img-' + idx" class="image-preview-item">
          <img :src="img.previewUrl" :alt="img.file.name" class="preview-thumb" />
          <!-- 解析中：图片置黑 + 解析动画 -->
          <div v-if="img.status === 'parsing'" class="attach-mask">
            <span class="attach-spinner"></span>
            <span class="attach-mask-text">解析中</span>
          </div>
          <!-- 解析完成：图片置黑 + 完成提示 -->
          <div v-else-if="img.status === 'done'" class="attach-mask attach-mask-done">
            <span class="attach-check">✓</span>
            <span class="attach-mask-text">解析完成</span>
          </div>
          <!-- 解析失败 -->
          <div v-else-if="img.status === 'error'" class="attach-mask attach-mask-error">
            <span class="attach-mask-text">解析失败</span>
          </div>
          <button class="preview-remove-btn" @click="removeImage(idx)" title="移除图片">×</button>
        </div>
        <!-- 文档预览（解析状态与图片一致） -->
        <div v-for="(doc, dIdx) in docPreviews" :key="'doc-' + dIdx" class="doc-preview-item" :class="'doc-' + doc.status">
          <span class="doc-preview-icon">📄</span>
          <span class="doc-preview-name">{{ doc.fileName }}</span>
          <span v-if="doc.status === 'parsing'" class="doc-preview-status">
            <span class="attach-spinner attach-spinner-small"></span> 解析中
          </span>
          <span v-else-if="doc.status === 'done'" class="doc-preview-status doc-status-done">✓ 解析完成<template v-if="doc.chunks"> · {{ doc.chunks }} 个片段</template></span>
          <span v-else-if="doc.status === 'error'" class="doc-preview-status doc-status-error">解析失败</span>
          <button class="preview-remove-btn doc-remove-btn" @click="removeDoc(dIdx)" title="移除文档">×</button>
        </div>
      </div>
      <!-- 图片编辑快捷指令气泡（图片解析完成后展示） -->
      <div v-if="imagePreviews.some(img => img.status === 'done')" class="image-quick-commands">
        <span class="guide-label">快捷编辑：</span>
        <button
          v-for="cmd in imageQuickCommands"
          :key="cmd.label"
          class="quick-cmd-chip"
          @click="clickImageQuickCommand(cmd)"
        >{{ cmd.label }}</button>
      </div>
      <div class="chat-input">
        <button class="upload-btn" @click="triggerUpload" :disabled="connectionStatus === 'connecting'" title="上传文档到 RAG 知识库">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="upload-btn-text">文档</span>
        </button>
        <input type="file" ref="fileInput" accept=".md,.txt,.doc,.docx,.pdf" @change="handleFileUpload" multiple hidden />
        <!-- 图片上传按钮 -->
        <button class="upload-btn image-upload-btn" @click="triggerImageUpload" :disabled="connectionStatus === 'connecting'" title="上传图片">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
          <span class="upload-btn-text">图片</span>
        </button>
        <input type="file" ref="imageInput" accept="image/jpeg,image/png,image/gif,image/webp" @change="handleImageSelect" multiple hidden />
        <!-- 生成恋爱报告按钮（仅恋爱大师页面展示） -->
        <button
          v-if="aiType === 'love'"
          class="upload-btn report-btn"
          @click="emit('generate-report')"
          :disabled="connectionStatus === 'connecting' || isParsing || reportLoading"
          title="根据当前对话生成恋爱报告"
        >
          <span v-if="reportLoading" class="attach-spinner attach-spinner-small"></span>
          <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          <span class="upload-btn-text">{{ reportLoading ? '生成中' : '报告' }}</span>
        </button>
        <textarea
          ref="inputRef"
          v-model="inputMessage"
          @keydown.enter.prevent="sendMessage"
          @input="resizeInput"
          placeholder="请输入消息..."
          class="input-box"
          :disabled="connectionStatus === 'connecting'"
        ></textarea>
        <!-- 生成中显示停止按钮，否则显示发送按钮 -->
        <button
          v-if="connectionStatus === 'connecting'"
          @click="emit('stop-generation')"
          class="stop-button"
          title="停止 AI 回复"
        ><span class="stop-icon"></span>停止</button>
        <button
          v-else
          @click="sendMessage"
          class="send-button"
          :disabled="isParsing || (!inputMessage.trim() && !hasReadyAttachments)"
        >{{ isParsing ? '解析中' : '发送' }}</button>
      </div>
      <!-- 输入框下方：深度思考 / 智能搜索 开关按钮（DeepSeek 风格胶囊） -->
      <div class="chat-input-toolbar">
        <button
          class="input-mode-chip"
          :class="{ active: deepThinkOn }"
          :disabled="connectionStatus === 'connecting'"
          :title="deepThinkOn ? '已开启深度思考：AI 先推理分析再回答' : '开启后 AI 将先进行深度推理分析再回答'"
          @click="deepThinkOn = !deepThinkOn"
        >
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2a7 7 0 0 0-4 12.7c.6.5 1 1.4 1 2.3h6c0-.9.4-1.8 1-2.3A7 7 0 0 0 12 2z"/>
            <line x1="9" y1="21" x2="15" y2="21"/>
          </svg>
          深度思考
        </button>
        <button
          class="input-mode-chip"
          :class="{ active: webSearchOn }"
          :disabled="connectionStatus === 'connecting'"
          :title="webSearchOn ? '已开启联网搜索：AI 回答前将检索网络信息' : '开启后 AI 将联网搜索最新信息辅助回答'"
          @click="webSearchOn = !webSearchOn"
        >
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          智能搜索
        </button>
      </div>
    </div>

    <!-- 语音输入悬浮按钮（右下角，FunASR 优先，浏览器语音识别降级） -->
    <button
      class="voice-float-btn"
      :class="{ recording: isRecording, connecting: isVoiceConnecting }"
      @click="toggleVoiceInput"
      :title="isRecording ? '点击停止录音' : '语音输入'"
    >
      <span v-if="isVoiceConnecting" class="attach-spinner attach-spinner-small"></span>
      <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
        <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
        <line x1="12" y1="19" x2="12" y2="23"/>
        <line x1="8" y1="23" x2="16" y2="23"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch, computed } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'
import { parseImage, parseRagDocument, confirmRagImport, discardRagDocument, exportLoveReport, replyToManus, API_BASE_URL } from '../api'
import { useVoiceInput } from '../composables/useVoiceInput'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  statusText: {
    type: String,
    default: ''
  },
  aiType: {
    type: String,
    default: 'default'  // 'love' 或 'super'
  },
  reportLoading: {
    type: Boolean,
    default: false
  },
  chatMode: {
    type: String,
    default: 'chat'  // 'chat' 恋爱咨询 / 'match' 对象推荐
  },
  matchGender: {
    type: String,
    default: ''  // 对象推荐期望性别：'' 不限 / '男' / '女'
  },
  showCapabilityGuide: {
    type: Boolean,
    default: false  // 是否展示能力引导气泡（首次对话后展示）
  }
})

const emit = defineEmits(['send-message', 'generate-report', 'update:chatMode', 'update:matchGender', 'stop-generation', 'quick-send'])

// 输入区能力开关：深度思考（AI 先推理再回答）/ 智能搜索（回答前联网检索）
const deepThinkOn = ref(false)
const webSearchOn = ref(false)

// 能力引导气泡：点击后自动填入输入框并发送
const capabilityGuides = [
  { label: '📝 约会计划', text: '帮我做一份浪漫的约会计划，生成 PDF 文件' },
  { label: '🖼️ 图片编辑', text: '帮我把上传的图片调成暖色调' },
  { label: '💌 恋爱报告', text: '', action: 'report' },
  { label: '🔍 联网搜索', text: '帮我搜索一下最近的恋爱心理学研究' }
]

const clickCapabilityGuide = (guide) => {
  if (guide.action === 'report') {
    emit('generate-report')
    return
  }
  inputMessage.value = guide.text
  nextTick(() => {
    resizeInput()
    sendMessage()
  })
}

// 恋爱大师模式切换选项
const chatModes = [
  { value: 'chat', label: '💬 恋爱咨询' },
  { value: 'match', label: '💘 对象推荐' }
]

const switchChatMode = (mode) => {
  if (mode !== props.chatMode) {
    emit('update:chatMode', mode)
  }
}

// 对象推荐期望性别选项（硬约束：后端按 gender 元数据过滤候选人）
const matchGenders = [
  { value: '', label: '不限' },
  { value: '男', label: '👦 男生' },
  { value: '女', label: '👧 女生' }
]

const switchMatchGender = (gender) => {
  if (gender !== props.matchGender) {
    emit('update:matchGender', gender)
  }
}

// 恋爱报告下载：三种格式选项与按钮 loading 状态（key: 消息索引-格式）
const reportFormats = [
  { value: 'pdf', label: 'PDF' },
  { value: 'word', label: 'Word' },
  { value: 'md', label: 'Markdown' }
]
const downloadingReports = reactive({})

const downloadReport = async (report, format, index) => {
  if (!report || downloadingReports[index + '-' + format]) return
  downloadingReports[index + '-' + format] = true
  try {
    await exportLoveReport(report, format)
  } catch (e) {
    console.error('下载恋爱报告失败:', e)
  } finally {
    downloadingReports[index + '-' + format] = false
  }
}

const inputMessage = ref('')
const messagesContainer = ref(null)
const inputRef = ref(null)
const fileInput = ref(null)
const imageInput = ref(null)
const uploadStatus = ref(null)

// ===== askHuman 交互式回复 =====
// 各提问卡片的回复草稿与提交中状态（key: 消息索引）
const humanReplyDrafts = reactive({})
const humanReplySending = reactive({})

// 提交用户对 askHuman 提问的回复
const submitHumanReply = async (msg, index) => {
  const answer = (humanReplyDrafts[index] || '').trim()
  if (!answer || humanReplySending[index] || msg.answered) return
  humanReplySending[index] = true
  try {
    const res = await replyToManus(msg.interactionId, answer)
    if (res && res.code === 0) {
      msg.answered = true
      msg.answer = answer
      delete humanReplyDrafts[index]
    } else {
      uploadStatus.value = { type: 'error', text: `回复提交失败: ${res?.message || '未知错误'}` }
      setTimeout(() => { uploadStatus.value = null }, 3000)
    }
  } catch (e) {
    // 后端统一异常处理返回 BaseResponse，优先展示其 message
    const errMsg = e.response?.data?.message || e.message
    uploadStatus.value = { type: 'error', text: `回复提交失败: ${errMsg}` }
    setTimeout(() => { uploadStatus.value = null }, 3000)
  } finally {
    humanReplySending[index] = false
  }
}

// ===== 语音输入（本地 FunASR 流式识别） =====
const { isRecording, isConnecting: isVoiceConnecting, voiceError, startVoiceInput, stopVoiceInput } = useVoiceInput()
// 开始录音时输入框已有的文本，识别结果追加在其后
let voiceBaseText = ''

const toggleVoiceInput = async () => {
  if (isRecording.value || isVoiceConnecting.value) {
    stopVoiceInput()
    return
  }
  voiceBaseText = inputMessage.value
  await startVoiceInput((text) => {
    // 识别文本实时回填到输入框
    inputMessage.value = voiceBaseText + text
    nextTick(() => resizeInput())
  })
  if (voiceError.value) {
    uploadStatus.value = { type: 'error', text: voiceError.value }
    setTimeout(() => { uploadStatus.value = null }, 4000)
  }
}

// 待上传附件（选择即解析，解析完成后随提示词一起发送）
// 图片：[{ file, previewUrl, url, understanding, status: 'parsing'|'done'|'error' }]
const imagePreviews = ref([])
// 文档：[{ fileName, parseId, chunks, status: 'parsing'|'done'|'error' }]
const docPreviews = ref([])

// 是否有附件正在解析（解析期间禁止发送）
const isParsing = computed(() =>
  imagePreviews.value.some(img => img.status === 'parsing') ||
  docPreviews.value.some(doc => doc.status === 'parsing')
)

// 是否有解析完成可发送的附件
const hasReadyAttachments = computed(() =>
  imagePreviews.value.some(img => img.status === 'done') ||
  docPreviews.value.some(doc => doc.status === 'done')
)

// 触发图片上传
const triggerImageUpload = () => {
  imageInput.value?.click()
}

// 处理图片选择：选择后立即上传后端解析（MIMO 视觉理解），解析期间展示置黑动画
const handleImageSelect = (event) => {
  const files = event.target.files
  if (!files || files.length === 0) return

  for (const file of files) {
    // 校验文件类型
    if (!file.type.startsWith('image/')) {
      uploadStatus.value = { type: 'error', text: `"${file.name}" 不是图片文件` }
      setTimeout(() => { uploadStatus.value = null }, 3000)
      continue
    }
    // 校验文件大小（10MB）
    if (file.size > 10 * 1024 * 1024) {
      uploadStatus.value = { type: 'error', text: `"${file.name}" 超过 10MB 限制` }
      setTimeout(() => { uploadStatus.value = null }, 3000)
      continue
    }
    // 生成预览 URL，立即进入解析中状态
    const previewUrl = URL.createObjectURL(file)
    const item = reactive({
      file,
      previewUrl,
      url: null,
      understanding: '',
      status: 'parsing'
    })
    imagePreviews.value.push(item)

    // 后端解析：上传保存 + 视觉理解一步完成
    parseImage(file).then(res => {
      if (res && res.success) {
        item.url = res.url
        item.understanding = res.understanding || ''
        item.status = 'done'
      } else {
        item.status = 'error'
        uploadStatus.value = { type: 'error', text: `"${file.name}" 解析失败: ${res?.message || '未知错误'}` }
        setTimeout(() => { uploadStatus.value = null }, 3000)
      }
    }).catch(e => {
      item.status = 'error'
      uploadStatus.value = { type: 'error', text: `"${file.name}" 解析出错: ${e.message}` }
      setTimeout(() => { uploadStatus.value = null }, 3000)
    })
  }

  // 重置 input
  event.target.value = ''
}

// 移除图片
const removeImage = (idx) => {
  const removed = imagePreviews.value.splice(idx, 1)[0]
  if (removed && removed.previewUrl) {
    URL.revokeObjectURL(removed.previewUrl)
  }
}

// 移除文档（同时丢弃后端已解析的缓存）
const removeDoc = (idx) => {
  const removed = docPreviews.value.splice(idx, 1)[0]
  if (removed && removed.parseId) {
    discardRagDocument(removed.parseId).catch(() => {})
  }
}

// 工具调用展开/折叠状态
const expandedSteps = reactive({})
const expandedToolCalls = reactive({})
// 思考过程展开/折叠状态（默认展开）
const expandedThinks = reactive({})

const toggleStep = (index) => {
  expandedSteps[index] = !expandedSteps[index]
}

const toggleToolCall = (stepIndex, tcIndex) => {
  const key = stepIndex + '-' + tcIndex
  expandedToolCalls[key] = !expandedToolCalls[key]
}

// 切换思考过程展开/折叠（undefined 视为展开）
const toggleThink = (index) => {
  expandedThinks[index] = expandedThinks[index] === false ? true : false
}

// 清理 URL 尾部可能被 AI 模型拼接的引号/标点/括号等非 URL 字符
const cleanTrailingUrlChars = (url) => {
  if (!url) return url
  let result = url.trim()
  while (result.length > 0 && /[)\]},;，。；：、"'`]$/.test(result)) {
    result = result.slice(0, -1)
  }
  return result
}

// 根据后端返回的路径构建完整下载 URL（绝对地址如 OSS 公网 URL 直接透传）
const buildFileUrl = (url) => {
  if (!url) return '#'
  const cleaned = cleanTrailingUrlChars(url)
  // OSS 等绝对地址无需拼接 API 前缀
  if (/^https?:\/\//.test(cleaned)) return cleaned
  // API_BASE_URL 已包含 /api，需去除匹配路径中的 /api 前缀避免重复
  return API_BASE_URL + cleaned.replace(/^\/api/, '')
}

// 将图片 URL 转换为完整可访问的 URL（用于预览 img 标签）
const resolveImageUrl = (url) => {
  if (!url) return '#'
  const cleaned = cleanTrailingUrlChars(url)
  // 如果是相对路径（以 /api/ 开头），拼接 API_BASE_URL
  if (cleaned.startsWith('/api/')) {
    return API_BASE_URL + cleaned.replace(/^\/api/, '')
  }
  // 如果已经是完整 http(s) URL，直接返回
  if (cleaned.startsWith('http://') || cleaned.startsWith('https://')) {
    return cleaned
  }
  // 其他相对路径，直接拼接
  return API_BASE_URL + (cleaned.startsWith('/') ? cleaned : '/' + cleaned)
}

// 构建下载 URL：对于本地图片追加 ?download=true 参数，由服务端设置 Content-Disposition: attachment
// 解决跨域下 <a download> 属性被浏览器静默忽略的问题
// 对于远程图片 URL（降级场景），直接返回原 URL，不拼接 download 参数
const resolveDownloadUrl = (url) => {
  if (!url || url === '#') return '#'
  // 如果是远程 URL（http/https），直接返回，由浏览器原生下载行为处理
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }
  // 本地 URL：先转为完整 URL
  const previewUrl = resolveImageUrl(url)
  if (!previewUrl || previewUrl === '#') return '#'
  const sep = previewUrl.includes('?') ? '&' : '?'
  return previewUrl + sep + 'download=true'
}

// 图片加载失败时显示占位提示
const onImageLoadError = (e) => {
  const img = e.target
  img.style.display = 'none'
  // 在图片位置显示文字提示
  const container = img.parentElement
  if (container) {
    let placeholder = container.querySelector('.image-error-placeholder')
    if (!placeholder) {
      placeholder = document.createElement('div')
      placeholder.className = 'image-error-placeholder'
      placeholder.textContent = '⚠️ 图片加载失败，请尝试下载查看'
      placeholder.style.cssText = 'padding:20px;text-align:center;color:#999;font-size:13px;background:#f5f5f5;border-radius:6px;width:100%;margin-top:8px;'
      img.after(placeholder)
    }
  }
}

// 查找前序用户消息中的引用图片（用于图片编辑对比展示）
const findOriginalImage = (currentIndex) => {
  for (let i = currentIndex - 1; i >= Math.max(0, currentIndex - 10); i--) {
    const msg = props.messages[i]
    if (msg && msg.isUser && msg.quote && msg.quote.images && msg.quote.images.length > 0) {
      const img = msg.quote.images[0]
      return img.url || img.previewUrl || null
    }
  }
  return null
}

// 图片编辑快捷指令（上传图片解析完成后展示）
const imageQuickCommands = [
  { label: '🌅 暖色调', text: '帮我把这张照片调成暖色调，营造温馨浪漫的氛围' },
  { label: '🎨 卡通版', text: '帮我把这张照片变成卡通风格的插画' },
  { label: '🏖️ 换背景', text: '帮我把这张照片的背景换成海边日落' },
  { label: '✂️ 去背景', text: '帮我去掉这张照片的背景，变成透明底' }
]

const clickImageQuickCommand = (cmd) => {
  inputMessage.value = cmd.text
  nextTick(() => {
    resizeInput()
    sendMessage()
  })
}

// 从 PDF 工具结果中提取下载 URL
const getDownloadUrl = (result) => {
  const match = result.match(/\/api\/files\/download\/pdf\/[^\s\]]+/)
  if (match) {
    // API_BASE_URL 已包含 /api，需要去除匹配路径中的 /api 前缀避免重复
    const path = cleanTrailingUrlChars(match[0].replace(/^\/api/, ''))
    if (!path) return '#'
    return API_BASE_URL + path
  }
  return '#'
}

// 从 writeFile 工具结果中提取文件名并构建下载 URL
const getFileDownloadUrl = (result) => {
  // 从 "File written successfully to: /path/to/file.ext" 中提取文件名
  const match = result.match(/File written successfully to:.*?([^\\\/]+)$/)
  if (match) {
    const fileName = cleanTrailingUrlChars(match[1].trim())
    if (!fileName) return '#'
    return `${API_BASE_URL}/files/download/file/${encodeURIComponent(fileName)}`
  }
  return '#'
}

const resizeInput = () => {
  const textarea = inputRef.value
  if (textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px'
  }
}

// 触发文件上传对话框
const triggerUpload = () => {
  fileInput.value?.click()
}

// 处理文档选择：选择后立即后端解析（沿用原有提取/切分/关键词逻辑，不入库），
// 解析完成后等待用户发送提示词，再根据提示词决定是否确认导入知识库
const handleFileUpload = (event) => {
  const files = event.target.files
  if (!files || files.length === 0) return

  for (const file of files) {
    const item = reactive({
      fileName: file.name,
      parseId: null,
      chunks: 0,
      textContent: '',
      status: 'parsing'
    })
    docPreviews.value.push(item)

    parseRagDocument(file).then(res => {
      if (res && res.success) {
        item.parseId = res.parseId
        item.chunks = res.chunks || 0
        item.textContent = res.text || ''
        item.status = 'done'
      } else {
        item.status = 'error'
        uploadStatus.value = { type: 'error', text: `"${file.name}" 解析失败: ${res?.message || '未知错误'}` }
        setTimeout(() => { uploadStatus.value = null }, 3000)
      }
    }).catch(e => {
      item.status = 'error'
      uploadStatus.value = { type: 'error', text: `"${file.name}" 解析出错: ${e.message}` }
      setTimeout(() => { uploadStatus.value = null }, 3000)
    })
  }

  // 重置 file input
  event.target.value = ''
}

// 根据AI类型选择不同头像
const aiAvatar = computed(() => {
  return props.aiType === 'love' 
    ? '/ai-love-avatar.png'  // 恋爱大师头像
    : '/ai-super-avatar.png' // 超级智能体头像
})

// 判断提示词是否含导入知识库意图
const hasImportIntent = (text) => {
  return /导入|入库|知识库|存入|保存|加入|记住|学习这|收录/.test(text || '')
}

// 发送消息：附件采用引用模式（图片/文档在上、提示词在下），只发送解析完成的附件
const sendMessage = () => {
  const text = inputMessage.value.trim()
  const readyImages = imagePreviews.value.filter(img => img.status === 'done')
  const readyDocs = docPreviews.value.filter(doc => doc.status === 'done')

  if (!text && readyImages.length === 0 && readyDocs.length === 0) return
  if (isParsing.value) return

  // 文档：根据提示词决定是否确认导入知识库
  const importDocs = readyDocs.length > 0 && (hasImportIntent(text) || !text)
  if (readyDocs.length > 0) {
    if (importDocs) {
      readyDocs.forEach(doc => {
        confirmRagImport(doc.parseId).then(res => {
          if (res && res.success) {
            uploadStatus.value = { type: 'success', text: `文档 "${doc.fileName}" 已导入知识库，共 ${res.chunks} 个片段` }
          } else {
            uploadStatus.value = { type: 'error', text: `文档 "${doc.fileName}" 导入失败: ${res?.message || '未知错误'}` }
          }
          setTimeout(() => { uploadStatus.value = null }, 4000)
        }).catch(e => {
          uploadStatus.value = { type: 'error', text: `文档 "${doc.fileName}" 导入出错: ${e.message}` }
          setTimeout(() => { uploadStatus.value = null }, 4000)
        })
      })
    } else {
      // 未检测到导入意图，丢弃后端解析缓存，不入库
      readyDocs.forEach(doc => {
        discardRagDocument(doc.parseId).catch(() => {})
      })
      uploadStatus.value = { type: 'info', text: '提示词未包含导入意图，文档未入库（可在提示词中说明“导入知识库”）' }
      setTimeout(() => { uploadStatus.value = null }, 4000)
    }
  }

  // 以引用模式交给父组件发送（只 emit 一次，避免图片重复发送）
  emit('send-message', text, {
    images: readyImages.map(img => ({
      url: img.url,
      previewUrl: img.previewUrl,
      fileName: img.file?.name || '图片',
      understanding: img.understanding
    })),
    docs: readyDocs.map(doc => ({
      fileName: doc.fileName,
      chunks: doc.chunks,
      textContent: doc.textContent || '',
      imported: importDocs
    })),
    // 能力开关状态：随消息透传给后端
    deepThink: deepThinkOn.value,
    webSearch: webSearchOn.value
  })

  inputMessage.value = ''
  imagePreviews.value = []
  docPreviews.value = []
  // 重置输入框高度
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
    }
  })
}

// 格式化时间
const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 自动滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 监听消息变化与内容变化，自动滚动
watch(() => props.messages.length, () => {
  scrollToBottom()
})

watch(() => props.messages.map(m => m.content).join(''), () => {
  scrollToBottom()
})

// 监听工具调用结果更新，自动滚动
const toolCallsSnapshot = computed(() => {
  return props.messages
    .filter(m => m.type === 'tool_step' && m.toolCalls)
    .flatMap(m => m.toolCalls.map(tc => tc.result || ''))
    .join('|')
})

watch(toolCallsSnapshot, () => {
  scrollToBottom()
})

// 自动展开最新的工具调用步骤
watch(() => props.messages.filter(m => m.type === 'tool_step').length, () => {
  const msgs = props.messages
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i].type === 'tool_step') {
      expandedSteps[i] = true
      break
    }
  }
})

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background-color: #f5f5f5;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: #c1c1c1 transparent;
}

.message-wrapper {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  width: 100%;
  flex-shrink: 0;
}

/* 聊天区滚动条美化 */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
  transition: background 0.2s;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: #a1a1a1;
}

/* 实时状态提示条：浮在消息列表末尾右侧，紧凑布局 */
.status-bar-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 16px;
  font-size: 13px;
  float: right;
  clear: both;
  margin: 4px 0 8px 0;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
  animation: statusPulse 2s ease-in-out infinite;
  white-space: nowrap;
}

@keyframes statusPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.85; }
}

.status-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  flex-shrink: 0;
}

.status-text {
  flex: 1;
}

.message {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-bottom: 8px;
}

.user-message {
  margin-left: auto; /* 用户消息靠右 */
  flex-direction: row; /* 正常顺序，先气泡后头像 */
}

.ai-message {
  margin-right: auto; /* AI消息靠左 */
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  margin-left: 8px; /* 用户头像在右侧，左边距 */
}

.ai-avatar {
  margin-right: 8px; /* AI头像在左侧，右边距 */
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #007bff;
  color: white;
  font-weight: bold;
}

.message-bubble {
  padding: 12px;
  border-radius: 18px;
  position: relative;
  word-wrap: break-word;
  min-width: 100px; /* 最小宽度 */
}

.user-message .message-bubble {
  background-color: #007bff;
  color: white;
  border-bottom-right-radius: 4px;
  text-align: left;
}

.ai-message .message-bubble {
  background-color: #e9e9eb;
  color: #333;
  border-bottom-left-radius: 4px;
  text-align: left;
}

.message-content {
  font-size: 16px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
}

.chat-input-container {
  background-color: white;
  border-top: 1px solid #e0e0e0;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

/* 恋爱大师模式切换置顶 tab 栏（豆包风格） */
.chat-mode-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px;
  background-color: white;
  border-bottom: 1px solid #f0e4e8;
  flex-shrink: 0;
  z-index: 5;
}

.chat-mode-chip {
  border: 1px solid #ffb3c6;
  border-radius: 16px;
  padding: 6px 18px;
  font-size: 13px;
  background: white;
  color: #e05575;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.chat-mode-chip:hover:not(:disabled) {
  background-color: #ffe9ee;
}

.chat-mode-chip.active {
  background-color: #ff6b8b;
  border-color: #ff6b8b;
  color: white;
  box-shadow: 0 2px 8px rgba(255, 107, 139, 0.35);
}

.chat-mode-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 对象推荐期望性别过滤 */
.gender-filter-divider {
  width: 1px;
  align-self: stretch;
  margin: 2px 2px;
  background-color: #ffd4de;
}

.gender-filter-label {
  align-self: center;
  font-size: 12px;
  color: #b98a96;
  white-space: nowrap;
}

.gender-chip {
  padding: 4px 10px;
  font-size: 12px;
}

.upload-status {
  padding: 6px 16px;
  font-size: 13px;
  text-align: center;
  animation: fadeIn 0.3s ease;
}

.upload-status.info {
  color: #666;
  background-color: #f0f0f0;
}

.upload-status.success {
  color: #155724;
  background-color: #d4edda;
}

.upload-status.error {
  color: #721c24;
  background-color: #f8d7da;
}

.chat-input {
  display: flex;
  padding: 10px 12px;
  gap: 6px;
  align-items: flex-end;
  flex-wrap: nowrap;
}

/* 输入框下方能力开关工具栏（深度思考 / 智能搜索） */
.chat-input-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px 10px;
}

.input-mode-chip {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #e3e3e3;
  border-radius: 16px;
  background: #fff;
  color: #666;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  user-select: none;
}

.input-mode-chip:hover:not(:disabled) {
  color: #ff6b8b;
  border-color: #ffb3c6;
  background-color: #fff5f7;
}

.input-mode-chip.active {
  color: #fff;
  background: linear-gradient(135deg, #ff6b8b, #ff8fa3);
  border-color: transparent;
  box-shadow: 0 2px 8px rgba(255, 107, 139, 0.35);
}

.input-mode-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 语音输入悬浮按钮：固定在聊天区右下角（输入区上方） */
.voice-float-btn {
  position: absolute;
  right: 20px;
  bottom: 96px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #ff6b8b, #ff8fa3);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(255, 107, 139, 0.45);
  transition: all 0.25s;
  z-index: 20;
}

.voice-float-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(255, 107, 139, 0.55);
}

/* 录音中：红色脉冲动画 */
.voice-float-btn.recording {
  background: linear-gradient(135deg, #e53935, #ff6f61);
  animation: voicePulse 1.2s ease-in-out infinite;
}

/* 连接中：静态提示色 */
.voice-float-btn.connecting {
  background: linear-gradient(135deg, #ffb74d, #ffa726);
}

@keyframes voicePulse {
  0%, 100% {
    box-shadow: 0 4px 16px rgba(229, 57, 53, 0.45), 0 0 0 0 rgba(229, 57, 53, 0.4);
  }
  50% {
    box-shadow: 0 4px 16px rgba(229, 57, 53, 0.45), 0 0 0 12px rgba(229, 57, 53, 0);
  }
}

.upload-btn {
  flex-shrink: 0;
  height: 38px;
  min-width: 38px;
  border: 1px solid #ddd;
  border-radius: 19px;
  padding: 0 10px;
  background: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #666;
  transition: all 0.2s;
  white-space: nowrap;
}

.upload-btn:hover:not(:disabled) {
  color: #007bff;
  border-color: #007bff;
  background-color: #f0f7ff;
}

.upload-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.upload-btn-text {
  font-size: 13px;
}

.input-box {
  flex-grow: 1;
  border: 1px solid #ddd;
  border-radius: 20px;
  padding: 10px 16px;
  font-size: 16px;
  resize: none;
  min-height: 20px;
  max-height: 150px; /* 自动调整高度，最高 150px */
  outline: none;
  transition: border-color 0.3s;
  overflow-y: auto;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE & Edge */
}

/* 隐藏Webkit浏览器的滚动条 */
.input-box::-webkit-scrollbar {
  display: none;
}

.input-box:focus {
  border-color: #007bff;
}

.send-button {
  flex-shrink: 0;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 20px;
  padding: 0 18px;
  font-size: 15px;
  cursor: pointer;
  transition: background-color 0.3s;
  height: 38px;
  white-space: nowrap;
}

.send-button:hover:not(:disabled) {
  background-color: #0069d9;
}

/* 停止生成按钮（红色，生成中替换发送按钮） */
.stop-button {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background-color: #ff4d5e;
  color: white;
  border: none;
  border-radius: 20px;
  padding: 0 16px;
  font-size: 15px;
  cursor: pointer;
  transition: background-color 0.3s;
  height: 38px;
  white-space: nowrap;
}

.stop-button:hover {
  background-color: #e63c4d;
}

.stop-icon {
  width: 11px;
  height: 11px;
  background: #fff;
  border-radius: 2px;
  display: inline-block;
}

.typing-indicator {
  display: inline-block;
  animation: blink 0.7s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0% { opacity: 0; }
  50% { opacity: 1; }
  100% { opacity: 0; }
}

.input-box:disabled, .send-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 工具调用步骤外包容器 */
.tool-step-wrapper {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-right: auto;
}

/* 工具调用步骤卡片样式 */
.tool-step-card {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  overflow: hidden;
  flex: 1;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.tool-step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: opacity 0.2s;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.tool-step-header:hover {
  opacity: 0.9;
}

.tool-step-arrow {
  font-size: 12px;
  transition: transform 0.2s;
  flex-shrink: 0;
}

.tool-step-title {
  font-weight: 600;
  font-size: 14px;
  flex: 1;
}

.tool-step-badge {
  background: rgba(255, 255, 255, 0.25);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  flex-shrink: 0;
}

.tool-step-body {
  padding: 8px 12px;
}

.tool-call-item {
  border: 1px solid #e9ecef;
  border-radius: 6px;
  margin: 6px 0;
  overflow: hidden;
  background: white;
}

.tool-call-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.15s;
  background: #f1f3f5;
}

.tool-call-header:hover {
  background: #e9ecef;
}

.tool-call-arrow {
  font-size: 10px;
  flex-shrink: 0;
  color: #6c757d;
}

.tool-call-name {
  font-weight: 500;
  font-size: 13px;
  color: #495057;
  flex: 1;
}

.tool-call-done {
  color: #28a745;
  font-size: 13px;
  flex-shrink: 0;
}

.tool-call-spinner {
  color: #ffc107;
  font-size: 14px;
  flex-shrink: 0;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.tool-call-detail {
  padding: 8px 12px 12px;
  border-top: 1px solid #f1f3f5;
}

.tool-call-section {
  margin-bottom: 8px;
}

.tool-call-section:last-child {
  margin-bottom: 0;
}

.tool-call-label {
  font-size: 12px;
  font-weight: 600;
  color: #6c757d;
  margin-bottom: 4px;
}

.tool-call-value {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
  color: #333;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

.tool-call-pending {
  color: #adb5bd;
  font-style: italic;
}

/* PDF 下载按钮 */
.tool-call-download {
  margin-top: 10px;
  text-align: center;
}

.pdf-download-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 6px;
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  transition: opacity 0.2s, transform 0.15s;
}

.pdf-download-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.pdf-download-btn svg {
  flex-shrink: 0;
}

/* 思考过程卡片（DeepSeek 风格） */
.think-wrapper {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-right: auto;
  margin-bottom: 12px;
}

.think-card {
  flex: 1;
  background: #f7f8fa;
  border: 1px solid #e6e8eb;
  border-left: 3px solid #b0b7c3;
  border-radius: 6px;
  overflow: hidden;
}

.think-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  cursor: pointer;
  user-select: none;
  color: #8b919a;
  font-size: 12px;
}

.think-header:hover {
  background: #eef0f3;
}

.think-icon {
  flex-shrink: 0;
}

.think-title {
  flex: 1;
  font-weight: 500;
}

.think-step {
  color: #9aa1ac;
  font-weight: 400;
}

.think-arrow {
  font-size: 10px;
  flex-shrink: 0;
  color: #9aa1ac;
}

.think-body {
  padding: 6px 10px;
  border-top: 1px solid #eceef1;
  font-size: 12px;
  line-height: 1.4;
  color: #9ca3af;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* 生成文件列表卡片 */
.file-list-wrapper {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-right: auto;
}

.file-list-card {
  flex: 1;
  background: #ffffff;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.file-list-title {
  font-size: 13px;
  font-weight: 600;
  color: #495057;
  margin-bottom: 10px;
}

.file-list-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.file-list-btn {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 恋爱报告卡片 */
.love-report-wrapper {
  display: flex;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 8px;
}

/* ===== askHuman 提问卡片 ===== */
.ask-human-wrapper {
  display: flex;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 8px;
}

.ask-human-card {
  flex: 1;
  max-width: 80%;
  background: linear-gradient(135deg, #fffbeb 0%, #ffffff 100%);
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(245, 158, 11, 0.12);
}

.ask-human-title {
  font-size: 15px;
  font-weight: 600;
  color: #b45309;
  margin-bottom: 8px;
}

.ask-human-question {
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 10px;
}

.ask-human-reply {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.ask-human-input {
  flex: 1;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  line-height: 1.5;
}

.ask-human-input:focus {
  border-color: #f59e0b;
}

.ask-human-submit-btn {
  flex-shrink: 0;
  background-color: #f59e0b;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 8px 18px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.ask-human-submit-btn:hover:not(:disabled) {
  background-color: #d97706;
}

.ask-human-submit-btn:disabled {
  background-color: #fcd9a0;
  cursor: not-allowed;
}

.ask-human-answered {
  font-size: 14px;
  color: #15803d;
  background-color: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  padding: 8px 10px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ===== 语音输入悬浮按钮（样式见 .voice-float-btn） ===== */

.love-report-card {
  flex: 1;
  max-width: 80%;
  background: linear-gradient(135deg, #fff5f7 0%, #ffffff 100%);
  border: 1px solid #ffd6e0;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(255, 107, 139, 0.12);
}

.love-report-title {
  font-size: 15px;
  font-weight: 600;
  color: #e05575;
  margin-bottom: 10px;
}

.love-report-list {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.love-report-item {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
}

.love-report-card .message-time {
  margin-top: 10px;
}

/* 报告下载按钮组 */
.love-report-downloads {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #ffd6e0;
}

.love-report-download-label {
  font-size: 12px;
  color: #e05575;
}

.love-report-download-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  font-size: 12px;
  color: #e05575;
  background: #fff;
  border: 1px solid #ffb3c6;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.love-report-download-btn:hover:not(:disabled) {
  background: #ffe9ee;
  border-color: #ff8fab;
}

.love-report-download-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 最终总结气泡高亮 */
.ai-summary .message-bubble {
  background: linear-gradient(135deg, #eef2ff 0%, #f5f0ff 100%);
  border: 1px solid #d6ddff;
  color: #2c3145;
}

/* 最终回答专属卡片 */
.summary-wrapper {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-right: auto;
}

.summary-card {
  flex: 1;
  background: linear-gradient(135deg, #f3f7ff 0%, #f6f0ff 100%);
  border: 1px solid #cfd8ff;
  border-radius: 12px;
  padding: 12px 16px;
  box-shadow: 0 2px 10px rgba(96, 110, 234, 0.12);
}

.summary-header {
  margin-bottom: 8px;
}

.summary-badge {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  color: #5b5bd6;
  background: rgba(91, 91, 214, 0.1);
  border-radius: 12px;
  padding: 2px 10px;
}

.summary-content {
  font-size: 15px;
  line-height: 1.7;
  color: #2c3145;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 已手动停止提示条 */
.stopped-wrapper {
  display: flex;
  justify-content: center;
  margin: 8px 0;
}

.stopped-card {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f3f4f6;
  color: #8a8f99;
  border: 1px solid #e2e4e9;
  border-radius: 16px;
  padding: 5px 14px;
  font-size: 13px;
}

.stopped-icon {
  color: #ff4d5e;
}

/* 响应式：工具调用卡片 */
@media (max-width: 768px) {
  .tool-step-wrapper {
    max-width: 95%;
  }
  .tool-step-card {
    margin: 4px 0;
  }
  .tool-step-header {
    padding: 8px 10px;
  }
  .tool-step-title {
    font-size: 13px;
  }
  .tool-call-value {
    font-size: 11px;
    max-height: 150px;
  }
}

@media (max-width: 480px) {
  .tool-step-wrapper {
    max-width: 100%;
  }
  .tool-step-card {
    border-radius: 6px;
  }
  .tool-step-header {
    padding: 6px 8px;
  }
  .tool-call-header {
    padding: 6px 8px;
  }
  .tool-call-detail {
    padding: 6px 8px 8px;
  }
  .tool-call-value {
    padding: 6px 8px;
    font-size: 11px;
    max-height: 120px;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message {
    max-width: 95%;
  }

  .message-content {
    font-size: 15px;
  }

  .chat-input {
    padding: 10px 12px;
  }

  .input-box {
    padding: 8px 12px;
  }

  .send-button {
    padding: 0 15px;
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .chat-container {
    border-radius: 0;
  }

  .chat-messages {
    padding: 10px 8px;
  }

  .message {
    max-width: 92%;
  }

  .avatar {
    width: 30px;
    height: 30px;
  }

  .message-bubble {
    padding: 8px 10px;
    border-radius: 14px;
  }

  .user-message .message-bubble {
    border-bottom-right-radius: 3px;
  }

  .ai-message .message-bubble {
    border-bottom-left-radius: 3px;
  }

  .message-content {
    font-size: 14px;
    line-height: 1.45;
  }

  .message-time {
    font-size: 11px;
  }

  .message-wrapper {
    margin-bottom: 10px;
  }

  .chat-input-container {
    border-top: 1px solid #e8e8e8;
  }

  .chat-input {
    padding: 8px 8px;
    gap: 6px;
  }

  .upload-btn {
    height: 34px;
    min-width: 34px;
    padding: 0 8px;
    border-radius: 17px;
  }

  .upload-btn-text {
    display: none;
  }

  .input-box {
    padding: 8px 12px;
    font-size: 15px;
    border-radius: 17px;
  }

  .send-button {
    height: 34px;
    padding: 0 14px;
    font-size: 14px;
    border-radius: 17px;
  }

  .upload-status {
    padding: 4px 10px;
    font-size: 12px;
  }
}

/* 新增：不同类型消息的样式 */
.ai-answer {
  animation: fadeIn 0.3s ease-in-out;
}

.ai-final {
  /* 最终回答，可以有不同的样式，例如边框高亮等 */
}

.ai-error {
  opacity: 0.7;
}

.user-question {
  /* 用户提问的特殊样式 */
}

/* 用户发送的图片消息 */
.user-images .message-bubble {
  padding: 8px;
  background: transparent;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.user-images .message-content {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.user-image-in-chat {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  object-fit: cover;
  border: 2px solid rgba(255,255,255,0.3);
}

.user-image-label {
  font-size: 12px;
  color: rgba(255,255,255,0.8);
  display: block;
  text-align: center;
  margin-bottom: 4px;
}

/* 连续消息气泡样式 */
.ai-message + .ai-message {
  margin-top: 4px;
}

.ai-message + .ai-message .avatar {
  visibility: hidden;
}

.ai-message + .ai-message .message-bubble {
  border-top-left-radius: 10px;
}

/* 图片预览区域 */
.image-previews {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px 0;
  background: white;
}

.image-preview-item {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid #e0e0e0;
  flex-shrink: 0;
}

.preview-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-remove-btn {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: none;
  background: #ff4444;
  color: white;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.preview-remove-btn:hover {
  background: #cc0000;
}

/* 解析状态遮罩：解析中/完成时图片置黑 */
.attach-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: white;
}

.attach-mask-done {
  background: rgba(0, 0, 0, 0.6);
}

.attach-mask-error {
  background: rgba(120, 20, 20, 0.7);
}

.attach-mask-text {
  font-size: 10px;
  line-height: 1;
}

.attach-check {
  font-size: 14px;
  color: #6fe89a;
  line-height: 1;
}

.attach-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  flex-shrink: 0;
}

.attach-spinner-small {
  width: 10px;
  height: 10px;
  border-width: 1.5px;
  display: inline-block;
  vertical-align: middle;
}

/* 文档待上传预览项 */
.doc-preview-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 22px 0 10px;
  border-radius: 16px;
  border: 1px solid #e0e0e0;
  background: #f8f9fa;
  font-size: 12px;
  color: #495057;
  max-width: 260px;
  flex-shrink: 0;
  transition: opacity 0.2s, background-color 0.2s;
}

/* 解析中/完成：文档项置暗 */
.doc-preview-item.doc-parsing,
.doc-preview-item.doc-done {
  background: #343a40;
  border-color: #343a40;
  color: #f1f3f5;
}

.doc-preview-item.doc-error {
  background: #fdf1f1;
  border-color: #f1c4c4;
  color: #a33;
}

.doc-preview-icon {
  flex-shrink: 0;
}

.doc-preview-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-preview-status {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  opacity: 0.9;
}

.doc-status-done {
  color: #6fe89a;
}

.doc-status-error {
  color: #d9534f;
}

.doc-remove-btn {
  top: 50%;
  transform: translateY(-50%);
  right: 3px;
}

/* 用户消息内的引用块（微信引用风格：附件在上，提示词在下） */
.quote-block {
  background: rgba(255, 255, 255, 0.16);
  border-left: 3px solid rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  padding: 8px;
  margin-bottom: 8px;
}

.quote-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.quote-image {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid rgba(255, 255, 255, 0.35);
}

.quote-doc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.92);
  padding: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quote-doc-chunks {
  opacity: 0.75;
  font-size: 12px;
}

.quote-doc-badge {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
  white-space: nowrap;
}

.quote-doc-imported {
  background-color: rgba(82, 196, 26, 0.2);
  color: #52c41a;
}

.quote-doc-skipped {
  background-color: rgba(0, 0, 0, 0.12);
  opacity: 0.85;
}

.quote-images + .quote-doc {
  margin-top: 6px;
}

.preview-loading {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0,0,0,0.6);
  color: white;
  font-size: 10px;
  text-align: center;
  padding: 2px 0;
}

.preview-done {
  position: absolute;
  top: 2px;
  left: 2px;
  background: #28a745;
  color: white;
  font-size: 10px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 图片上传按钮 */
.image-upload-btn {
  /* 复用 upload-btn 样式 */
}

/* 响应式附件预览 */
@media (max-width: 480px) {
  .image-preview-item {
    width: 48px;
    height: 48px;
  }
  .quote-image {
    width: 72px;
    height: 72px;
  }
}

/* 生成的图片预览卡片 */
.generated-image-wrapper {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-right: auto;
}

.generated-image-card {
  flex: 1;
  background: #ffffff;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.generated-image-title {
  font-size: 13px;
  font-weight: 600;
  color: #495057;
  align-self: flex-start;
}

.generated-image-preview {
  width: 100%;
  max-width: 320px;
  max-height: 400px;
  border-radius: 6px;
  object-fit: contain;
  background: #f5f5f5;
  border: 1px solid #eee;
}

.generated-image-download-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 6px;
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  transition: opacity 0.2s, transform 0.15s;
}

.generated-image-download-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.generated-image-download-btn svg {
  flex-shrink: 0;
}

@media (max-width: 480px) {
  .generated-image-wrapper {
    max-width: 100%;
  }
  .generated-image-preview {
    max-width: 100%;
    max-height: 280px;
  }
}

/* 能力引导气泡栏 */
.capability-guide-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #fff5f7, #fef9fc);
  border-top: 1px solid #fdeef0;
  flex-shrink: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.capability-guide-bar::-webkit-scrollbar {
  display: none;
}

.guide-label {
  font-size: 12px;
  color: #b98a96;
  white-space: nowrap;
  flex-shrink: 0;
}

.guide-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 14px;
  border-radius: 16px;
  border: 1px solid #ffd6e0;
  background: #fff;
  font-size: 12px;
  color: #e05575;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
  flex-shrink: 0;
}

.guide-chip:hover {
  background: #ffe9ee;
  border-color: #ff8fab;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(255, 107, 139, 0.15);
}

/* 图片编辑对比卡片 */
.image-compare-card {
  flex: 1;
  background: #fff;
  border: 1px solid #dee2e6;
  border-radius: 10px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.image-compare-title {
  font-size: 13px;
  font-weight: 600;
  color: #495057;
  margin-bottom: 12px;
}

.image-compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.image-compare-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.image-compare-label {
  font-size: 11px;
  font-weight: 600;
  color: #888;
  padding: 2px 10px;
  border-radius: 10px;
  background: #f5f5f5;
}

.image-compare-label.result-label {
  background: #fff5f7;
  color: #e05575;
}

.image-compare-img {
  width: 100%;
  max-width: 200px;
  height: 160px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #eee;
  background: #f9f9f9;
}

.image-compare-actions {
  display: flex;
  justify-content: center;
  margin-top: 10px;
}

@media (max-width: 480px) {
  .image-compare-grid {
    grid-template-columns: 1fr;
  }
  .image-compare-img {
    max-width: 100%;
    max-height: 200px;
  }
}

/* 图片编辑快捷指令气泡 */
.image-quick-commands {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px 0;
  background: white;
  overflow-x: auto;
  scrollbar-width: none;
}

.image-quick-commands::-webkit-scrollbar {
  display: none;
}

.quick-cmd-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 14px;
  border: 1px solid #d4edda;
  background: #f0fff4;
  font-size: 12px;
  color: #2d7a4f;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
  flex-shrink: 0;
}

.quick-cmd-chip:hover {
  background: #e0f7ea;
  border-color: #a3d9b1;
  transform: translateY(-1px);
}
</style> 