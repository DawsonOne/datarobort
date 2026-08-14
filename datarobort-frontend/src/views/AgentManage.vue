<template>
  <div class="page">
    <!-- Toolbar -->
    <div class="toolbar">
      <div style="flex:1;color:#64748b;font-size:13px">
        绑定数据源、知识库与自定义 Prompt，发布后即可在对话页选择使用
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新增智能体
      </el-button>
    </div>

    <!-- Table -->
    <el-table :data="agents" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column label="数据源" min-width="140">
        <template #default="{ row }">
          <template v-if="row.datasourceNames && row.datasourceNames.length">
            <el-tag v-for="n in row.datasourceNames" :key="n" size="small" style="margin:1px 3px 1px 0">{{ n }}</el-tag>
          </template>
          <span v-else style="color:#94a3b8">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column label="知识库" min-width="140">
        <template #default="{ row }">
          <template v-if="row.kbNames && row.kbNames.length">
            <el-tag v-for="n in row.kbNames" :key="n" size="small" type="success" style="margin:1px 3px 1px 0">{{ n }}</el-tag>
          </template>
          <span v-else style="color:#94a3b8">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column label="业务知识召回" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.businessRecallEnabled ? 'success' : 'info'" size="small">
            {{ row.businessRecallEnabled ? '开启' : '关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="语义模型召回" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.semanticRecallEnabled ? 'success' : 'info'" size="small">
            {{ row.semanticRecallEnabled ? '开启' : '关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small" effect="dark">
            {{ row.status === 1 ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="160">
        <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status !== 1" size="small" text type="success" @click="doPublish(row, 1)">发布</el-button>
          <el-button v-else size="small" text type="warning" @click="doPublish(row, 0)">下线</el-button>
          <el-popconfirm title="确定删除该智能体？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button size="small" text type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑智能体' : '新增智能体'"
      width="640px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：销售分析智能体" />
        </el-form-item>
        <el-form-item label="系统 Prompt">
          <el-input
            v-model="form.prompt"
            type="textarea"
            :rows="5"
            placeholder="业务背景 / 角色设定，会注入 SQL 生成与数据洞察环节，如：你是电商销售数据分析师，重点关注销售额、订单量与毛利率。"
          />
        </el-form-item>
        <el-form-item label="绑定数据源">
          <el-select v-model="form.datasourceIds" multiple clearable placeholder="选择数据源（SQL 将在此库执行）" style="width:100%">
            <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定知识库">
          <el-select v-model="form.kbIds" multiple clearable placeholder="选择知识库（用于知识召回）" style="width:100%">
            <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务知识召回">
          <el-switch v-model="form.businessRecallEnabled" />
          <span style="margin-left:10px;color:#94a3b8;font-size:12px">术语/同义词库召回</span>
        </el-form-item>
        <el-form-item label="语义模型召回">
          <el-switch v-model="form.semanticRecallEnabled" />
          <span style="margin-left:10px;color:#94a3b8;font-size:12px">表/字段同义词召回</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listAgents, createAgent, updateAgent, deleteAgent, publishAgent } from '../api/agent'
import { listDatasources } from '../api/datasource'
import { listKBs } from '../api/knowledge'

const agents = ref([])
const datasources = ref([])
const kbs = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const formRef = ref(null)

const form = reactive({
  name: '',
  prompt: '',
  datasourceIds: [],
  kbIds: [],
  businessRecallEnabled: true,
  semanticRecallEnabled: true,
})

const rules = {
  name: [{ required: true, message: '请输入智能体名称', trigger: 'blur' }],
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 19) : '—'
}

async function fetchList() {
  loading.value = true
  try {
    agents.value = await listAgents()
  } finally {
    loading.value = false
  }
}

async function fetchOptions() {
  try {
    const [ds, kb] = await Promise.all([listDatasources(), listKBs()])
    datasources.value = ds || []
    kbs.value = kb || []
  } catch (e) { /* options failure is non-fatal */ }
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, { name: '', prompt: '', datasourceIds: [], kbIds: [], businessRecallEnabled: true, semanticRecallEnabled: true })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    prompt: row.prompt || '',
    datasourceIds: [...(row.datasourceIds || [])],
    kbIds: [...(row.kbIds || [])],
    businessRecallEnabled: row.businessRecallEnabled !== false,
    semanticRecallEnabled: row.semanticRecallEnabled !== false,
  })
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
}

async function doSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      name: form.name,
      prompt: form.prompt,
      datasourceIds: form.datasourceIds,
      kbIds: form.kbIds,
      businessRecallEnabled: form.businessRecallEnabled,
      semanticRecallEnabled: form.semanticRecallEnabled,
    }
    if (isEdit.value) {
      await updateAgent(editingId.value, payload)
      ElMessage.success('智能体已更新')
    } else {
      await createAgent(payload)
      ElMessage.success('智能体已创建')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) { /* interceptor shows the message */ } finally {
    saving.value = false
  }
}

async function doPublish(row, status) {
  await publishAgent(row.id, status)
  ElMessage.success(status === 1 ? '已发布' : '已下线')
  fetchList()
}

async function doDelete(id) {
  await deleteAgent(id)
  ElMessage.success('已删除')
  fetchList()
}

onMounted(() => {
  fetchList()
  fetchOptions()
})
</script>

<style scoped>
.page { padding: 4px; }
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 14px;
}
</style>
