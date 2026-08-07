<template>
  <div class="page">
    <div class="toolbar">
      <span style="font-weight:700;font-size:15px">业务知识（同义词）</span>
      <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增术语</el-button>
    </div>

    <el-table :data="list" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="term" label="术语" min-width="140" />
      <el-table-column prop="synonyms" label="同义词" min-width="240">
        <template #default="{ row }"><span style="font-size:13px">{{ row.synonyms }}</span></template>
      </el-table-column>
      <el-table-column label="向量状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.vectorStatus === 'done' ? 'success' : row.vectorStatus === 'failed' ? 'danger' : 'warning'">
            {{ row.vectorStatus === 'done' ? '已向量化' : row.vectorStatus === 'failed' ? '失败' : '待处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="召回" width="70">
        <template #default="{ row }"><el-tag size="small" :type="row.recallEnabled ? 'success' : 'info'">{{ row.recallEnabled ? '开' : '关' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确定删除？" @confirm="doDelete(row.id)">
            <template #reference><el-button size="small" text type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="isEdit ? '编辑术语' : '新增术语'" width="500px" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="术语" prop="term"><el-input v-model="form.term" placeholder="如：GMV" /></el-form-item>
        <el-form-item label="同义词"><el-input v-model="form.synonyms" placeholder="逗号分隔，如：成交额,销售额" /></el-form-item>
        <el-form-item label="开启召回"><el-switch v-model="form.recallEnabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listBK, createBK, updateBK, deleteBK } from '../api/knowledge'

const list = ref([]); const loading = ref(false)
const dialog = ref(false); const isEdit = ref(false); const editId = ref(null); const saving = ref(false)
const formRef = ref(null)
const form = reactive({ term: '', synonyms: '', recallEnabled: true })
const rules = { term: [{ required: true, message: '请输入术语', trigger: 'blur' }] }

function resetForm() { form.term = ''; form.synonyms = ''; form.recallEnabled = true; editId.value = null; isEdit.value = false }
async function fetch() { loading.value = true; try { list.value = await listBK() } finally { loading.value = false } }
function openCreate() { resetForm(); dialog.value = true }
function openEdit(row) {
  resetForm(); isEdit.value = true; editId.value = row.id
  form.term = row.term; form.synonyms = row.synonyms || ''; form.recallEnabled = !!row.recallEnabled
  dialog.value = true
}
async function doSave() {
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    const p = { term: form.term, synonyms: form.synonyms || null, recallEnabled: form.recallEnabled }
    if (isEdit.value) await updateBK(editId.value, p); else await createBK(p)
    dialog.value = false; fetch()
  } finally { saving.value = false }
}
async function doDelete(id) { try { await deleteBK(id) } finally { fetch() } }
onMounted(fetch)
</script>

<style scoped>
.page { max-width: 1000px; margin: 0 auto; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
</style>
