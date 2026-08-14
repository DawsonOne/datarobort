<template>
  <div class="chat-page">
    <!-- Top bar: agent selector + new chat -->
    <div class="chat-topbar">
      <el-select v-model="agentId" placeholder="选择智能体（不选则走默认链路）" clearable
                 size="default" style="width: 260px" @change="onAgentChange">
        <el-option v-for="a in agents" :key="a.id" :label="a.name + (a.status === 1 ? '' : '（草稿）')" :value="a.id" />
      </el-select>
      <el-button @click="newChat"><el-icon><Plus /></el-icon>新建对话</el-button>
      <span v-if="currentConversation" class="conv-title" :title="currentConversation.title">
        {{ currentConversation.title }}
      </span>
    </div>

    <div class="chat-body">
      <!-- Conversation sidebar -->
      <div class="conv-sidebar">
        <div class="conv-sidebar-head">
          <span>历史会话</span>
          <el-button text size="small" @click="fetchConversations"><el-icon><Refresh /></el-icon></el-button>
        </div>
        <div class="conv-list">
          <div v-for="c in conversations" :key="c.id" class="conv-item"
               :class="{ active: c.id === conversationId }" @click="loadConversation(c)">
            <div class="conv-item-title">{{ c.title }}</div>
            <div class="conv-item-time">{{ formatTime(c.updateTime) }}</div>
          </div>
          <el-empty v-if="conversations.length === 0" description="暂无会话" :image-size="50" />
        </div>
      </div>

      <!-- Messages area -->
      <div class="chat-messages" ref="msgContainer">
        <div v-if="messages.length === 0" class="empty-hint">
          <el-icon style="font-size:48px;color:#cbd5e1"><ChatDotRound /></el-icon>
          <p style="margin-top:12px;color:#94a3b8">向 DataRobort 提问，开始数据分析</p>
        </div>

        <div v-for="(msg, i) in messages" :key="i" class="msg-wrapper">
          <!-- User message -->
          <div v-if="msg.role === 'user'" class="msg-user">
            <div class="msg-bubble user-bubble">{{ msg.content }}</div>
          </div>

          <!-- Assistant message -->
          <div v-else class="msg-assistant">
            <div class="msg-bubble assistant-bubble">
              <!-- Node traces -->
              <div v-if="msg.traces && msg.traces.length > 0" class="traces-panel">
                <div v-for="t in msg.traces" :key="t.node" class="trace-row">
                  <el-icon :color="t.status === 'done' ? '#10b981' : t.status === 'failed' ? '#ef4444' : '#f59e0b'">
                    <component :is="t.status === 'done' ? 'CircleCheckFilled' : t.status === 'failed' ? 'CircleCloseFilled' : 'Loading'" />
                  </el-icon>
                  <span class="trace-node">{{ t.node }}</span>
                  <span v-if="t.message" class="trace-msg">{{ t.message }}</span>
                  <span v-if="t.durationMs" class="trace-time">{{ t.durationMs }}ms</span>
                </div>
              </div>

              <!-- Stream indicator -->
              <div v-if="msg.streaming && msg.traces && msg.traces.length > 0" style="color:#94a3b8;font-size:12px;margin-top:4px">
                <el-icon><Loading /></el-icon> 分析中...
              </div>

              <!-- Report markdown -->
              <div v-if="msg.markdownReport" class="report-content" v-html="renderMarkdown(msg.markdownReport)"></div>

              <!-- Report file (HTML with embedded charts) -->
              <div v-if="msg.reportFileUrl" class="report-file">
                <div class="report-file-head">
                  <el-icon color="#4F46E5"><Document /></el-icon>
                  <span class="report-file-title">分析报告文件</span>
                  <el-button size="small" type="primary" plain
                             @click="msg.showReportFile = !msg.showReportFile">
                    {{ msg.showReportFile ? '收起' : '查看' }}
                  </el-button>
                  <a class="report-open" :href="msg.reportFileUrl" target="_blank">新窗口打开</a>
                </div>
                <iframe v-if="msg.showReportFile" :src="msg.reportFileUrl"
                        class="report-iframe" title="分析报告"></iframe>
              </div>

              <!-- Chart (ECharts fallback, when no report file) -->
              <div v-if="msg.chartOption && !msg.reportFileUrl" class="chart-container">
                <div :ref="el => setChartRef(i, el)" style="width:100%;height:360px"></div>
              </div>

              <!-- Raw result -->
              <div v-if="msg.sql" class="sql-display">
                <el-collapse>
                  <el-collapse-item title="SQL 查询">
                    <pre>{{ msg.sql }}</pre>
                  </el-collapse-item>
                </el-collapse>
              </div>

              <!-- Error -->
              <div v-if="msg.failed" class="error-msg">{{ msg.errorMessage || '分析失败' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input area -->
    <div class="chat-input">
      <el-input v-model="question" placeholder="输入数据分析问题，如：上个月各类目销售额是多少？"
                @keyup.enter="doSend" :disabled="sending" clearable size="large">
        <template #append>
          <el-button type="primary" @click="doSend" :loading="sending" :icon="Promotion">发送</el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { useChatStream } from '../api/chat'
import { listAgents } from '../api/agent'
import { listConversations, createConversation, listMessages } from '../api/conversation'

const { result, send } = useChatStream()
const question = ref('')
const sending = ref(false)
const messages = reactive([])
const msgContainer = ref(null)
const chartRefs = {}

// P4: agent + conversation state
const agents = ref([])
const agentId = ref(null)
const conversationId = ref(null)
const conversations = ref([])
const currentConversation = ref(null)

function setChartRef(idx, el) {
  if (el) chartRefs[idx] = el
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').substring(5, 16) : ''
}

onMounted(() => {
  result.value = { streaming: false, intent: '', sql: '', chartOption: null, markdownReport: '', reportFileUrl: '', traces: [], failed: false, errorMessage: '', complete: false }
  fetchAgents()
  fetchConversations()
})

async function fetchAgents() {
  try {
    agents.value = await listAgents()
  } catch (e) { /* non-fatal */ }
}

async function fetchConversations() {
  try {
    conversations.value = await listConversations(agentId.value || undefined)
  } catch (e) { /* non-fatal */ }
}

function onAgentChange() {
  // Switching agent starts a new chat; keep the current conversation when re-selecting the same agent
  newChat()
  fetchConversations()
}

function newChat() {
  if (sending.value) {
    ElMessage.warning('分析进行中，请稍候')
    return
  }
  conversationId.value = null
  currentConversation.value = null
  messages.splice(0)
}

async function loadConversation(c) {
  if (sending.value) {
    ElMessage.warning('分析进行中，请稍候')
    return
  }
  conversationId.value = c.id
  currentConversation.value = c
  // Sync the agent selector with the conversation's agent binding
  agentId.value = c.agentId || null
  try {
    const msgs = await listMessages(c.id)
    messages.splice(0)
    for (const m of msgs) {
      messages.push({
        role: m.role,
        content: m.content,
        traces: parseTraces(m.nodeTraces),
        markdownReport: m.markdownReport || '',
        chartOption: null,
        reportFileUrl: m.reportFileUrl || '',
        showReportFile: false,
        sql: m.sqlText || '',
        failed: false,
        errorMessage: '',
        streaming: false,
      })
    }
    scrollToBottom()
  } catch (e) { /* interceptor shows message */ }
}

function parseTraces(json) {
  if (!json) return []
  try {
    const t = JSON.parse(json)
    return Array.isArray(t) ? t : []
  } catch { return [] }
}

function scrollToBottom() {
  nextTick(() => {
    if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  })
}

/** Current assistant message index while streaming, -1 if not streaming */
let streamingMsgIdx = -1
/** Watcher cleanup functions */
let stopWatchers = null

/** Start watching result.value for real-time SSE updates */
function setupStreamWatchers() {
  if (stopWatchers) stopWatchers()

  // Watch traces array changes — update UI in real-time as node events arrive
  const stopTraces = watch(
    () => result.value.traces,
    (newTraces) => {
      if (streamingMsgIdx >= 0 && messages[streamingMsgIdx]) {
        messages[streamingMsgIdx].traces = [...newTraces]
      }
    },
    { deep: true }
  )

  // Watch streaming complete flag — apply final result
  const stopComplete = watch(
    () => result.value.complete,
    (isComplete) => {
      if (!isComplete || streamingMsgIdx < 0 || !messages[streamingMsgIdx]) return

      const msg = messages[streamingMsgIdx]
      msg.markdownReport = result.value.markdownReport || ''
      msg.chartOption = result.value.chartOption || null
      msg.reportFileUrl = result.value.reportFileUrl || ''
      msg.showReportFile = !!result.value.reportFileUrl
      msg.sql = result.value.sql || ''
      msg.failed = result.value.failed || false
      msg.errorMessage = result.value.errorMessage || ''
      msg.streaming = false
      msg.traces = [...result.value.traces]

      // Render ECharts fallback (only when no report file) after DOM update
      nextTick(() => {
        if (msg.chartOption && !msg.reportFileUrl) {
          const el = chartRefs[streamingMsgIdx]
          if (el) {
            const chart = echarts.init(el)
            chart.setOption(msg.chartOption)
          }
        }
        // Scroll to bottom
        if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight
      })

      streamingMsgIdx = -1
      sending.value = false
      if (stopWatchers) { stopWatchers(); stopWatchers = null }
    }
  )

  // Watch failed flag — handle errors arriving outside of complete
  const stopFailed = watch(
    () => result.value.failed,
    (isFailed) => {
      if (!isFailed || streamingMsgIdx < 0 || !messages[streamingMsgIdx]) return
      messages[streamingMsgIdx].failed = true
      messages[streamingMsgIdx].errorMessage = result.value.errorMessage || '分析失败'
    }
  )

  stopWatchers = () => {
    stopTraces(); stopComplete(); stopFailed()
  }
}

onBeforeUnmount(() => {
  if (stopWatchers) stopWatchers()
})

async function doSend() {
  const q = question.value.trim()
  if (!q || sending.value) return
  sending.value = true
  question.value = ''

  // Create a conversation on the first message of this session
  try {
    if (!conversationId.value) {
      const conv = await createConversation({ agentId: agentId.value, title: q.slice(0, 30) })
      conversationId.value = conv.id
      currentConversation.value = conv
      fetchConversations()
    }
  } catch (e) {
    ElMessage.error('创建会话失败: ' + (e.message || '未知错误'))
    sending.value = false
    question.value = q
    return
  }

  // Add user message
  messages.push({ role: 'user', content: q, traces: [], markdownReport: '', chartOption: null, reportFileUrl: '', showReportFile: false, sql: '', failed: false, errorMessage: '', streaming: false })

  // Add assistant placeholder message
  streamingMsgIdx = messages.length
  messages.push({ role: 'assistant', content: '', traces: [], markdownReport: '', chartOption: null, reportFileUrl: '', showReportFile: false, sql: '', failed: false, errorMessage: '', streaming: true })

  // Set up reactive watchers BEFORE sending so they capture all SSE events
  setupStreamWatchers()

  // Scroll to bottom after adding messages
  await nextTick()
  if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight

  // Now fire the SSE request — watchers update the UI reactively
  await send(q, { agentId: agentId.value, conversationId: conversationId.value })

  // Refresh the conversation list (new title / updated time)
  fetchConversations()
}

// Simple markdown → HTML renderer
function renderMarkdown(md) {
  if (!md) return ''
  let html = md
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    // Headers
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    .replace(/^# (.+)$/gm, '<h2>$1</h2>')
    // Bold / italic
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    // Inline code
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // Code blocks
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    // Blockquote
    .replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')
    // Line breaks
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')
  return '<p>' + html + '</p>'
}
</script>

<style scoped>
.chat-page {
  display: flex; flex-direction: column; height: calc(100vh - 120px); max-width: 1200px; margin: 0 auto;
}
.chat-topbar {
  display: flex; align-items: center; gap: 10px; padding: 8px 0 10px;
  border-bottom: 1px solid #e6e9f2;
}
.conv-title { font-size: 13px; color: #64748b; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-body { display: flex; flex: 1; min-height: 0; gap: 12px; }
.conv-sidebar {
  width: 200px; flex: none; border-right: 1px solid #e6e9f2; display: flex; flex-direction: column;
}
.conv-sidebar-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px; font-size: 13px; font-weight: 700; color: #475569;
}
.conv-list { flex: 1; overflow-y: auto; padding: 0 8px 8px; }
.conv-item {
  padding: 8px 10px; border-radius: 8px; cursor: pointer; margin-bottom: 4px;
}
.conv-item:hover { background: #eef2ff; }
.conv-item.active { background: #eef2ff; border: 1px solid #c7d2fe; }
.conv-item-title { font-size: 13px; color: #1e293b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-item-time { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.chat-messages {
  flex: 1; overflow-y: auto; padding: 16px 0; min-width: 0;
}
.empty-hint {
  display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%;
}
.msg-wrapper { margin-bottom: 16px; }
.msg-user { display: flex; justify-content: flex-end; }
.msg-bubble { max-width: 80%; padding: 12px 16px; border-radius: 12px; font-size: 14px; line-height: 1.6; }
.user-bubble { background: #4F46E5; color: #fff; }
.assistant-bubble { background: #fff; border: 1px solid #e6e9f2; }
.traces-panel { margin-bottom: 12px; padding: 10px 12px; background: #f8fafc; border-radius: 8px; border: 1px solid #e6e9f2; }
.trace-row { display: flex; align-items: center; gap: 8px; font-size: 12px; padding: 2px 0; }
.trace-node { font-weight: 600; color: #475569; min-width: 60px; }
.trace-msg { color: #64748b; flex: 1; }
.trace-time { color: #94a3b8; }
.sql-display { margin-top: 8px; }
.sql-display pre { font-size: 12px; white-space: pre-wrap; word-break: break-all; }
.chart-container { margin: 16px 0; }
.report-file { margin: 12px 0; border: 1px solid #e6e9f2; border-radius: 10px; overflow: hidden; }
.report-file-head { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: #f8fafc; border-bottom: 1px solid #e6e9f2; }
.report-file-title { font-size: 13px; font-weight: 600; color: #475569; flex: 1; }
.report-open { font-size: 12px; color: #4F46E5; text-decoration: none; }
.report-open:hover { text-decoration: underline; }
.report-iframe { width: 100%; height: 520px; border: none; display: block; background: #fff; }
.report-content { margin: 8px 0; }
.report-content :deep(h2) { font-size: 18px; margin: 12px 0 8px; }
.report-content :deep(h3) { font-size: 16px; margin: 10px 0 6px; }
.report-content :deep(h4) { font-size: 14px; margin: 8px 0 4px; }
.report-content :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 12px; border-radius: 8px; overflow-x: auto; font-size: 12px; }
.report-content :deep(code) { background: #f1f5f9; padding: 2px 4px; border-radius: 4px; font-size: 12px; }
.report-content :deep(blockquote) { border-left: 3px solid #e6e9f2; padding-left: 12px; color: #64748b; margin: 8px 0; }
.error-msg { color: #ef4444; font-size: 13px; margin-top: 8px; }
.chat-input { padding: 12px 0; }
</style>
