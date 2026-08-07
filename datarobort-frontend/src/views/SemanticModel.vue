<template>
  <div class="page">
    <div class="toolbar">
      <span style="font-weight:700;font-size:15px">语义模型（表/字段同义词）</span>
      <div style="display:flex;gap:8px">
        <el-select v-model="selectedDs" placeholder="选择数据源" @change="fetch" style="width:200px">
          <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
        </el-select>
        <el-button type="primary" :disabled="!selectedDs" @click="openCreate"><el-icon><Plus /></el-icon>新增配置</el-button>
      </div>
    </div>

    <div v-if="!selectedDs" style="text-align:center;padding:60px 0;color:#94a3b8">
      <el-icon style="font-size:36px"><Connection /></el-icon>
      <p style="margin-top:10px">👆 请先选择数据源，再配置表/字段同义词</p>
    </div>
    <el-table v-else :data="list" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="tableName" label="表名" width="160" />
      <el-table-column label="字段" width="160">
        <template #default="{ row }"><span :style="{ color: row.columnName ? '#1e293b' : '#94a3b8' }">{{ row.columnName || '(表级)' }}</span></template>
      </el-table-column>
      <el-table-column prop="synonyms" label="同义词" min-width="240" />
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

    <el-dialog v-model="dialog" :title="isEdit ? '编辑语义模型' : '新增语义模型'" width="560px" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="数据源" prop="dsId">
          <el-select v-model="form.dsId" :disabled="isEdit" style="width:100%" placeholder="选择数据源" @change="loadSchema">
            <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="表名" prop="tableName">
          <el-select v-model="form.tableName" :disabled="isEdit" style="width:100%" placeholder="选择表" @change="onTableChange">
            <el-option v-for="t in tables" :key="t.tableName" :label="t.tableName + (t.tableComment ? ' (' + t.tableComment + ')' : '')" :value="t.tableName" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段名">
          <el-select v-model="form.columnName" :disabled="isEdit" style="width:100%" placeholder="留空表示表级同义词" clearable>
            <el-option v-for="c in columns" :key="c.columnName" :label="c.columnName + ' (' + (c.dataType || '') + ')' + (c.columnComment ? ' ' + c.columnComment : '')" :value="c.columnName" />
          </el-select>
        </el-form-item>
        <el-form-item label="同义词" prop="synonyms"><el-input v-model="form.synonyms" placeholder="逗号分隔，如：成交额,销售额,总交易额" /></el-form-item>
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
import { listDatasources, getSchema } from '../api/datasource'
import { listSM, createSM, updateSM, deleteSM } from '../api/knowledge'

const datasources = ref([]); const list = ref([]); const loading = ref(false)
const selectedDs = ref(null); const tables = ref([]); const columns = ref([])

const dialog = ref(false); const isEdit = ref(false); const editId = ref(null); const saving = ref(false)
const formRef = ref(null)
const form = reactive({ dsId: null, tableName: '', columnName: null, synonyms: '', recallEnabled: true })
const rules = {
  dsId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
  tableName: [{ required: true, message: '请选择表', trigger: 'change' }],
  synonyms: [{ required: true, message: '请输入同义词', trigger: 'blur' }],
}

function resetForm() { form.dsId = null; form.tableName = ''; form.columnName = null; form.synonyms = ''; form.recallEnabled = true; editId.value = null; isEdit.value = false; tables.value = []; columns.value = [] }

async function fetchDatasources() { datasources.value = (await listDatasources()) || [] }
async function fetch() { if (!selectedDs.value) return; loading.value = true; try { list.value = await listSM(selectedDs.value) } finally { loading.value = false } }

async function loadSchema(dsId) { if (!dsId) return; tables.value = (await getSchema(dsId)) || []; columns.value = [] }
function onTableChange(tn) { const t = tables.value.find(x => x.tableName === tn); columns.value = t?.columns || [] }

function openCreate() { resetForm(); dialog.value = true }
function openEdit(row) {
  resetForm(); isEdit.value = true; editId.value = row.id
  form.dsId = row.dsId; form.tableName = row.tableName; form.columnName = row.columnName; form.synonyms = row.synonyms || ''; form.recallEnabled = !!row.recallEnabled
  loadSchema(row.dsId).then(() => onTableChange(row.tableName))
  dialog.value = true
}
async function doSave() {
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    const p = { dsId: form.dsId, tableName: form.tableName, columnName: form.columnName || null, synonyms: form.synonyms, recallEnabled: form.recallEnabled }
    if (isEdit.value) await updateSM(editId.value, p); else await createSM(p)
    dialog.value = false; fetch()
  } finally { saving.value = false }
}
async function doDelete(id) { try { await deleteSM(id) } finally { fetch() } }

onMounted(fetchDatasources)
</script>

<style scoped>
.page { max-width: 1100px; margin: 0 auto; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
</style>
