<template>
  <div class="page">
    <!-- Toolbar -->
    <div class="toolbar">
      <div style="flex:1;color:#64748b;font-size:13px">历史对话会话，点击「查看」浏览消息记录</div>
      <el-button @click="fetchList"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>

    <!-- Table -->
    <el-table :data="conversations" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column label="智能体" width="120">
        <template #default="{ row }">
          <span v-if="row.agentId">#{{ row.agentId }}</span>
          <span v-else style="color:#94a3b8">默认链路</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openDetail(row)">查看</el-button>
          <el-popconfirm title="确定删除该会话？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button size="small" text type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- Detail drawer -->
    <el-drawer v-model="drawerVisible" :title="current?.title || '会话详情'" size="560px">
      <div v-loading="messagesLoading" class="msg-list">
        <div v-for="m in messages" :key="m.id" class="msg-item" :class="m.role">
          <div class="msg-role">{{ m.role === 'user' ? '用户' : '助手' }}</div>
          <div class="msg-time">{{ formatTime(m.createTime) }}</div>
          <div class="msg-content">{{ m.content }}</div>
          <el-collapse v-if="m.sqlText" style="margin-top:6px">
            <el-collapse-item title="SQL 查询">
              <pre style="font-size:12px;white-space:pre-wrap">{{ m.sqlText }}</pre>
            </el-collapse-item>
          </el-collapse>
          <a v-if="m.reportFileUrl" class="report-link" :href="reportUrl(m.reportFileUrl)" target="_blank">查看报告文件</a>
        </div>
        <el-empty v-if="!messagesLoading && messages.length === 0" description="暂无消息" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listConversations, listMessages, deleteConversation } from '../api/conversation'

const conversations = ref([])
const loading = ref(false)
const drawerVisible = ref(false)
const messagesLoading = ref(false)
const current = ref(null)
const messages = ref([])

function formatTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 19) : '—'
}

function reportUrl(u) {
  if (!u) return '#'
  if (u.startsWith('http')) return u
  return 'http://localhost:8080' + u
}

async function fetchList() {
  loading.value = true
  try {
    conversations.value = await listConversations()
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  current.value = row
  drawerVisible.value = true
  messagesLoading.value = true
  try {
    messages.value = await listMessages(row.id)
  } finally {
    messagesLoading.value = false
  }
}

async function doDelete(id) {
  await deleteConversation(id)
  ElMessage.success('已删除')
  fetchList()
}

onMounted(fetchList)
</script>

<style scoped>
.page { padding: 4px; }
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 14px;
}
.msg-list { display: flex; flex-direction: column; gap: 12px; }
.msg-item {
  padding: 10px 14px; border-radius: 10px; background: #fff; border: 1px solid #e6e9f2;
}
.msg-item.user { background: #eef2ff; border-color: #c7d2fe; }
.msg-role { font-size: 12px; font-weight: 700; color: #4F46E5; }
.msg-item.user .msg-role { color: #2563EB; }
.msg-time { font-size: 11px; color: #94a3b8; margin: 2px 0 6px; }
.msg-content { font-size: 13px; color: #1e293b; white-space: pre-wrap; word-break: break-word; }
.report-link { font-size: 12px; color: #4F46E5; text-decoration: none; display: inline-block; margin-top: 6px; }
.report-link:hover { text-decoration: underline; }
</style>
