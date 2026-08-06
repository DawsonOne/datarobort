<template>
  <div class="page">
    <div class="toolbar">
      <span style="font-weight:700;font-size:15px">知识库管理</span>
      <el-button type="primary" @click="openCreateKB"><el-icon><Plus /></el-icon>新建知识库</el-button>
    </div>

    <!-- KB List -->
    <el-table :data="kbs" border stripe v-loading="loading" @row-click="selectKb" highlight-current-row style="cursor:pointer">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="分块策略" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.chunkStrategy === 'fixed' ? '' : row.chunkStrategy === 'heading' ? 'success' : 'warning'">{{ row.chunkStrategy }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkSize" label="分块大小" width="90" />
      <el-table-column prop="chunkOverlap" label="重叠" width="70" />
      <el-table-column label="召回" width="70">
        <template #default="{ row }"><el-tag size="small" :type="row.recallEnabled ? 'success' : 'info'">{{ row.recallEnabled ? '开' : '关' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click.stop="openEditKB(row)">编辑</el-button>
          <el-popconfirm title="确定删除？将清理所有文档和向量。" @confirm="doDeleteKB(row.id)">
            <template #reference><el-button size="small" text type="danger" @click.stop>删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- Document area (shown when a KB is selected) -->
    <div v-if="selectedKb" style="margin-top:20px">
      <div class="toolbar">
        <span style="font-weight:700">{{ selectedKb.name }} · 文档列表</span>
        <el-upload :action="''" :auto-upload="false" :show-file-list="false"
                   :on-change="onFileSelect" accept=".pdf,.docx,.md,.txt">
          <el-button type="success"><el-icon><Upload /></el-icon>上传文档</el-button>
        </el-upload>
      </div>
      <el-table :data="docs" border stripe v-loading="docLoading" size="small">
        <el-table-column prop="id" label="ID" width="50" />
        <el-table-column prop="filename" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="70" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'parsed' ? 'success' : row.status === 'failed' ? 'danger' : 'warning'">
              {{ row.status === 'parsed' ? '已解析' : row.status === 'failed' ? '失败' : '解析中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="150" show-overflow-tooltip>
          <template #default="{ row }"><span style="color:#ef4444;font-size:12px">{{ row.errorMsg }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-popconfirm title="确定删除此文档？" @confirm="doDeleteDoc(row.id)">
              <template #reference><el-button size="small" text type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- KB Create/Edit Dialog -->
    <el-dialog v-model="kbDialog" :title="isEdit ? '编辑知识库' : '新建知识库'" width="560px" destroy-on-close @closed="resetKbForm">
      <el-form ref="kbFormRef" :model="kbForm" :rules="kbRules" label-width="110px">
        <el-form-item label="名称" prop="name"><el-input v-model="kbForm.name" placeholder="知识库名称" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="kbForm.description" type="textarea" :rows="2" placeholder="可选" /></el-form-item>
        <el-form-item label="绑定Embedding模型" prop="embeddingModelId">
          <el-select v-model="kbForm.embeddingModelId" placeholder="选择 Embedding 模型" style="width:100%">
            <el-option v-for="m in embeddingModels" :key="m.id" :label="m.name + ' (' + (m.dimension || '?') + '维)'" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分块策略" prop="chunkStrategy">
          <el-select v-model="kbForm.chunkStrategy" style="width:100%">
            <el-option label="固定长度" value="fixed" />
            <el-option label="按标题层级" value="heading" />
            <el-option label="自定义分隔符" value="delimiter" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="kbForm.chunkStrategy === 'fixed' || kbForm.chunkStrategy === 'heading'" label="分块大小/重叠">
          <el-input-number v-model="kbForm.chunkSize" :min="100" :max="5000" style="width:120px" /> 字 /
          <el-input-number v-model="kbForm.chunkOverlap" :min="0" :max="500" style="width:100px" /> 字重叠
        </el-form-item>
        <el-form-item v-if="kbForm.chunkStrategy === 'delimiter'" label="分隔符">
          <el-input v-model="kbForm.delimiter" placeholder="如：\n\n 或 ###" />
        </el-form-item>
        <el-form-item label="开启召回">
          <el-switch v-model="kbForm.recallEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSaveKB">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listKBs, createKB, updateKB, deleteKB, listDocuments, uploadDocument, deleteDocument } from '../api/knowledge'
import { listModels } from '../api/model'

const kbs = ref([]); const loading = ref(false)
const selectedKb = ref(null); const docs = ref([]); const docLoading = ref(false)
const embeddingModels = ref([])

const kbDialog = ref(false); const isEdit = ref(false); const editId = ref(null); const saving = ref(false)
const kbFormRef = ref(null)
const kbForm = reactive({ name: '', description: '', chunkStrategy: 'fixed', chunkSize: 500, chunkOverlap: 50, delimiter: '', embeddingModelId: null, recallEnabled: true })
const kbRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }], embeddingModelId: [{ required: true, message: '请选择Embedding模型', trigger: 'change' }] }

function resetKbForm() {
  kbForm.name = ''; kbForm.description = ''; kbForm.chunkStrategy = 'fixed'; kbForm.chunkSize = 500; kbForm.chunkOverlap = 50
  kbForm.delimiter = ''; kbForm.embeddingModelId = null; kbForm.recallEnabled = true; editId.value = null; isEdit.value = false
}

async function fetchKBs() { loading.value = true; try { kbs.value = await listKBs() } finally { loading.value = false } }
async function fetchEmbeddingModels() { embeddingModels.value = (await listModels('embedding')) || [] }

function selectKb(row) { selectedKb.value = row; fetchDocs() }
async function fetchDocs() { if (!selectedKb.value) return; docLoading.value = true; try { docs.value = await listDocuments(selectedKb.value.id) } finally { docLoading.value = false } }

function openCreateKB() { resetKbForm(); kbDialog.value = true }
function openEditKB(row) {
  resetKbForm(); isEdit.value = true; editId.value = row.id
  kbForm.name = row.name; kbForm.description = row.description || ''; kbForm.chunkStrategy = row.chunkStrategy
  kbForm.chunkSize = row.chunkSize; kbForm.chunkOverlap = row.chunkOverlap; kbForm.delimiter = row.delimiter || ''
  kbForm.embeddingModelId = row.embeddingModelId; kbForm.recallEnabled = !!row.recallEnabled
  kbDialog.value = true
}

async function doSaveKB() {
  try { await kbFormRef.value.validate() } catch { return }
  saving.value = true
  try {
    const p = { name: kbForm.name, description: kbForm.description || null, chunkStrategy: kbForm.chunkStrategy, chunkSize: kbForm.chunkSize, chunkOverlap: kbForm.chunkOverlap, delimiter: kbForm.delimiter || null, embeddingModelId: kbForm.embeddingModelId, recallEnabled: kbForm.recallEnabled }
    if (isEdit.value) await updateKB(editId.value, p); else await createKB(p)
    kbDialog.value = false; fetchKBs()
  } finally { saving.value = false }
}

async function doDeleteKB(id) { await deleteKB(id); if (selectedKb.value?.id === id) { selectedKb.value = null; docs.value = [] }; fetchKBs() }

async function onFileSelect(uploadFile) {
  if (!selectedKb.value) return
  await uploadDocument(selectedKb.value.id, uploadFile.raw)
  fetchDocs()
}

async function doDeleteDoc(docId) { await deleteDocument(selectedKb.value.id, docId); fetchDocs() }

onMounted(() => { fetchKBs(); fetchEmbeddingModels() })
</script>

<style scoped>
.page { max-width: 1200px; margin: 0 auto; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
</style>
