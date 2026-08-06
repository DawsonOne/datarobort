<template>
  <div class="page-ds">
    <!-- Left: Datasource list -->
    <div class="left-panel">
      <div class="toolbar">
        <span style="font-weight:700">数据源列表</span>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon>新增
        </el-button>
      </div>
      <el-table :data="datasources" border stripe v-loading="loading" highlight-current-row
                @row-click="onSelect" style="cursor:pointer" size="small">
        <el-table-column prop="id" label="ID" width="50" />
        <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="70" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click.stop="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="success" @click.stop="doTest(row)">测试</el-button>
            <el-popconfirm title="确定删除？会移除关联的元数据。" @confirm="doDelete(row.id)">
              <template #reference>
                <el-button size="small" text type="danger" @click.stop>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Right: Schema Tree -->
    <div class="right-panel">
      <div v-if="!selectedDs" class="empty-hint">
        <el-empty description="请选择一个数据源查看 Schema" />
      </div>
      <div v-else class="schema-area">
        <div class="toolbar">
          <span style="font-weight:700">{{ selectedDs.name }} · Schema</span>
          <div style="display:flex;gap:8px">
            <el-button size="small" type="success" @click="doTest(selectedDs)">
              连接测试
            </el-button>
            <el-button size="small" type="primary" :loading="refreshing" @click="doRefreshSchema">
              刷新元数据
            </el-button>
          </div>
        </div>

        <!-- Connection info -->
        <div class="ds-info">
          <span>{{ selectedDs.type }}</span>
          <code style="font-size:11px;color:#64748b;">{{ selectedDs.jdbcUrl }}</code>
        </div>

        <!-- Schema tree -->
        <div v-if="schemaTree.length === 0 && !refreshing" style="padding:20px;text-align:center;color:#94a3b8">
          <p>暂无元数据</p>
          <p style="font-size:12px;margin-top:4px">点击「刷新元数据」抓取表结构</p>
        </div>
        <el-tree
          v-else
          :data="treeData"
          :props="treeProps"
          node-key="id"
          default-expand-all
          highlight-current
          style="margin-top:8px;background:transparent"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <el-icon v-if="data.tableName" color="#2563EB" style="margin-right:6px"><Grid /></el-icon>
              <el-icon v-else color="#0D9488" style="margin-right:6px"><Operation /></el-icon>
              <span :style="{ fontWeight: data.tableName ? 700 : 400 }">
                {{ data.tableName || data.columnName }}
              </span>
              <template v-if="data.dataType">
                <el-tag size="small" type="info" style="margin-left:8px;font-size:11px">{{ data.dataType }}</el-tag>
                <el-tag v-if="data.isPrimary" size="small" type="danger" style="margin-left:4px;font-size:11px">PK</el-tag>
                <el-tag v-if="!data.nullable" size="small" type="warning" style="margin-left:4px;font-size:11px">NOT NULL</el-tag>
              </template>
              <span v-if="data.tableComment" style="margin-left:8px;font-size:11px;color:#94a3b8">
                — {{ data.tableComment }}
              </span>
              <span v-if="data.columnComment" style="margin-left:8px;font-size:11px;color:#94a3b8">
                {{ data.columnComment }}
              </span>
            </span>
          </template>
        </el-tree>
      </div>
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑数据源' : '新增数据源'"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：演示业务库" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" :disabled="isEdit" style="width:100%">
            <el-option label="MySQL" value="mysql" />
            <el-option label="PostgreSQL" value="postgresql" />
          </el-select>
        </el-form-item>
        <el-form-item label="JDBC URL" prop="jdbcUrl">
          <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://host:port/db?..." />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="数据库用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则保留原密码' : '数据库密码'"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选" />
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
    <el-dialog v-model="testVisible" title="连接测试结果" width="420px">
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
import { ref, reactive, computed, onMounted } from 'vue'
import {
  listDatasources, createDatasource, updateDatasource, deleteDatasource,
  testDatasource, refreshSchema, getSchema
} from '../api/datasource'

const datasources = ref([])
const loading = ref(false)
const selectedDs = ref(null)
const schemaTree = ref([])
const refreshing = ref(false)

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const saving = ref(false)
const formRef = ref(null)

const testVisible = ref(false)
const testResult = ref(null)

const form = reactive({
  name: '',
  type: 'mysql',
  jdbcUrl: '',
  username: '',
  password: '',
  description: '',
  statusBool: true,
})

const rules = {
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  jdbcUrl: [{ required: true, message: '请输入 JDBC URL', trigger: 'blur' }],
}

/** Transform flat schema into tree nodes for el-tree. */
const treeData = computed(() => {
  return schemaTree.value.map(t => ({
    id: t.id,
    tableName: t.tableName,
    tableComment: t.tableComment,
    children: (t.columns || []).map(c => ({
      id: `col-${t.id}-${c.columnName}`,
      columnName: c.columnName,
      dataType: c.dataType,
      columnComment: c.columnComment,
      isPrimary: c.isPrimary,
      nullable: c.nullable,
    })),
  }))
})

const treeProps = {
  children: 'children',
  label: 'label',
}

function resetForm() {
  form.name = ''
  form.type = 'mysql'
  form.jdbcUrl = ''
  form.username = ''
  form.password = ''
  form.description = ''
  form.statusBool = true
  editId.value = null
  isEdit.value = false
}

async function fetchList() {
  loading.value = true
  try {
    datasources.value = await listDatasources()
  } finally {
    loading.value = false
  }
}

async function onSelect(row) {
  selectedDs.value = row
  await loadSchema(row.id)
}

async function loadSchema(dsId) {
  schemaTree.value = await getSchema(dsId)
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
  form.jdbcUrl = row.jdbcUrl
  form.username = row.username || ''
  form.password = ''
  form.description = row.description || ''
  form.statusBool = row.status !== 0
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
      jdbcUrl: form.jdbcUrl,
      username: form.username || null,
      password: form.password || null,
      description: form.description || null,
      status: form.statusBool ? 1 : 0,
    }
    if (isEdit.value) {
      if (!payload.password) delete payload.password
      await updateDatasource(editId.value, payload)
    } else {
      await createDatasource(payload)
    }
    dialogVisible.value = false
    await fetchList()
    // Re-select if editing
    if (isEdit.value && selectedDs.value?.id === editId.value) {
      await loadSchema(editId.value)
    }
  } finally {
    saving.value = false
  }
}

async function doDelete(id) {
  await deleteDatasource(id)
  if (selectedDs.value?.id === id) {
    selectedDs.value = null
    schemaTree.value = []
  }
  await fetchList()
}

async function doTest(row) {
  testVisible.value = true
  testResult.value = null
  testResult.value = await testDatasource(row.id)
}

async function doRefreshSchema() {
  if (!selectedDs.value) return
  refreshing.value = true
  try {
    const result = await refreshSchema(selectedDs.value.id)
    if (result && result.success) {
      await loadSchema(selectedDs.value.id)
    }
  } finally {
    refreshing.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.page-ds {
  display: flex;
  gap: 16px;
  height: calc(100vh - 120px);
  max-width: 1400px;
  margin: 0 auto;
}
.left-panel {
  width: 420px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(15,23,42,.06);
  padding: 16px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.right-panel {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(15,23,42,.06);
  padding: 16px;
  overflow-y: auto;
}
.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.tree-node {
  display: flex;
  align-items: center;
  font-size: 13.5px;
}
.ds-info {
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #64748b;
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
