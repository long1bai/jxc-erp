<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="项目管理">
        <template #icon><OfficeBuilding /></template>
        <el-button type="primary" size="small" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新增项目
        </el-button>
      </PageHeader>
    </template>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索项目名称/编号/客户"
        clearable
        style="width: 260px"
        @keyup.enter="load(1)"
        @clear="load(1)"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
      <el-button size="small" @click="resetSearch">重置</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="items" v-loading="loading" size="small" stripe v-if="!isMobile">
      <el-table-column prop="code" label="编号" width="110" />
      <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="customer_name" label="客户" min-width="140" show-overflow-tooltip />
      <el-table-column label="材料成本" width="120" align="right" sortable>
        <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
      </el-table-column>
      <el-table-column prop="po_count" label="采购单数" width="90" align="right" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'done' ? 'info' : 'success'" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无项目" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>项目名称</span><b>{{ row.name }}</b></div>
          <div class="m-row"><span>编号</span><b>{{ row.code || '—' }}</b></div>
          <div class="m-row"><span>客户</span><b>{{ row.customer_name }}</b></div>
          <div class="m-row"><span>材料成本</span><b>￥{{ fmt(row.total_amount) }}</b></div>
          <div class="m-row"><span>采购单数</span><b>{{ row.po_count }}</b></div>
          <div class="m-row"><span>备注</span><b>{{ row.remark || '—' }}</b></div>
        </div>
        <div class="m-actions">
          <el-button link type="primary" size="small" @click.stop="openDialog(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
        </div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>

    <!-- 分页 -->
    <el-pagination
      class="pager"
      background
      layout="total, prev, pager, next, sizes"
      :total="total"
      :page-size="size"
      :current-page="page"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="load"
      @size-change="onSizeChange"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑项目' : '新增项目'" width="500px">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="80px">
        <el-form-item label="编号"><el-input v-model="form.code" placeholder="项目编号（可选，如 P2026-001）" /></el-form-item>
        <el-form-item label="名称" required prop="name">
          <el-input v-model="form.name" placeholder="项目名称（如 XX 设备项目/XX 工地）" />
        </el-form-item>
        <el-form-item label="客户" required prop="customerId">
          <el-select v-model="form.customerId" filterable placeholder="选择客户（甲方）" style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">进行中</el-radio>
            <el-radio value="done">已完成</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi as api } from '../api/project'
import { baseApi } from '../api/base'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
const formRef = ref(null)
const customers = ref([])
const formRules = {
  name: [{ required: true, message: '请填写项目名称', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
}
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, code: '', name: '', customerId: null, status: 'active', remark: '' })

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function statusText(s) {
  return s === 'done' ? '已完成' : '进行中'
}

function onSizeChange(s) {
  size.value = s
  load(1)
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await api.projects({ keyword: keyword.value, page: page.value, size: size.value })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadCustomers() {
  try {
    const res = await baseApi.customers({ size: 100 })
    customers.value = res.data.items
  } catch { /* 忽略 */ }
}

function resetSearch() {
  keyword.value = ''
  load(1)
}

function openDialog(row) {
  Object.assign(form, row ? { id: row.id, code: row.code || '', name: row.name, customerId: row.customer_id ?? null, status: row.status || 'active', remark: row.remark || '' }
    : { id: null, code: '', name: '', customerId: null, status: 'active', remark: '' })
  dialogVisible.value = true
}

async function save() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    if (form.id) {
      await api.updateProject(form.id, form)
    } else {
      await api.createProject(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除项目「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteProject(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => { load(1); loadCustomers() })
</script>

<style scoped>

/* 手机卡片 */
.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }

</style>
