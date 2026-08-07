<template>
  <div class="page">
    <!-- Toolbar -->
    <div class="toolbar">
      <el-radio-group v-model="filterType" @change="fetchList">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="chat">Chat</el-radio-button>
        <el-radio-button value="embedding">Embedding</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新增模型
      </el-button>
    </div>

    <!-- Table -->
    <el-table :data="models" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="row.type === 'chat' ? 'primary' : 'success'" size="small" effect="dark">
            {{ row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="provider" label="提供商" width="100" />
      <el-table-column prop="modelName" label="模型名" min-width="160" />
      <el-table-column label="维度" width="80">
        <template #default="{ row }">
          <span v-if="row.type === 'embedding' && row.dimension">{{ row.dimension }}</span>
          <span v-else style="color:#94a3b8">—</span>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="warning" size="small" effect="dark">默认</el-tag>
          <span v-else style="color:#94a3b8">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" text type="success" @click="doTest(row)">测试</el-button>
          <el-button v-if="!row.isDefault" size="small" text type="warning" @click="doSetDefault(row)">设默认</el-button>
          <el-popconfirm title="确定删除该模型？" @confirm="doDelete(row.id)">
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
      :title="isEdit ? '编辑模型' : '新增模型'"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：通义千问-Plus" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" :disabled="isEdit" style="width:100%">
            <el-option label="Chat" value="chat" />
            <el-option label="Embedding" value="embedding" />
          </el-select>
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-input v-model="form.provider" placeholder="qwen / deepseek / vllm" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则保留原密钥' : '输入 API Key'"
          />
        </el-form-item>
        <el-form-item label="模型名" prop="modelName">
          <el-input v-model="form.modelName" placeholder="qwen-plus / text-embedding-v3" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :disabled="isEdit && form.isDefault" />
          <span style="margin-left:8px;font-size:12px;color:#94a3b8">
            每种类型仅一个默认模型
          </span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- Test Result Dialog -->
    <el-dialog v-model="testVisible" title="连通性测试结果" width="420px">
      <div v-if="testResult" style="text-align:center">
        <div style="font-size:48px;margin-bottom:12px">
          {{ testResult.success ? '✅' : '❌' }}
        </div>
        <p style="font-size:15px;margin-bottom:8px">{{ testResult.message }}</p>
        <p style="font-size:12px;color:#94a3b8">耗时：{{ testResult.latencyMs }}ms</p>
      </div>
      <el-empty v-else description="正在测试..." />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { listModels, createModel, updateModel, deleteModel, setDefaultModel, testModel } from '../api/model'

const models = ref([])
const loading = ref(false)
const filterType = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const saving = ref(false)
const formRef = ref(null)

const testVisible = ref(false)
const testResult = ref(null)

const form = reactive({
  name: '',
  type: '',
  provider: '',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  isDefault: false,
  statusBool: true,
})

const rules = {
  name: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名', trigger: 'blur' }],
}

function resetForm() {
  form.name = ''
  form.type = ''
  form.provider = ''
  form.baseUrl = ''
  form.apiKey = ''
  form.modelName = ''
  form.isDefault = false
  form.statusBool = true
  editId.value = null
  isEdit.value = false
}

async function fetchList() {
  loading.value = true
  try {
    models.value = await listModels(filterType.value || undefined)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  isEdit.value = true
  editId.value = row.id
  form.name = row.name
  form.type = row.type
  form.provider = row.provider || ''
  form.baseUrl = row.baseUrl
  form.apiKey = ''
  form.modelName = row.modelName
  form.isDefault = !!row.isDefault
  form.statusBool = row.status === 1
  dialogVisible.value = true
}

async function doSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      type: form.type,
      provider: form.provider || null,
      baseUrl: form.baseUrl,
      apiKey: form.apiKey || null,
      modelName: form.modelName,
      isDefault: form.isDefault,
      status: form.statusBool ? 1 : 0,
    }
    if (isEdit.value) {
      if (!payload.apiKey) delete payload.apiKey  // keep old key
      await updateModel(editId.value, payload)
    } else {
      await createModel(payload)
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

async function doDelete(id) {
  try { await deleteModel(id) } finally { fetchList() }
}

async function doSetDefault(row) {
  try { await setDefaultModel(row.id) } finally { fetchList() }
}

async function doTest(row) {
  testVisible.value = true
  testResult.value = null
  testResult.value = await testModel(row.id)
}
</script>

<style scoped>
.page { max-width: 1200px; margin: 0 auto; }
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
</style>
