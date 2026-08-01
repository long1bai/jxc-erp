<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="用户管理">
        <template #icon><User /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新增用户
        </el-button>
      </PageHeader>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
              title="账号与报工员工自动联动：新增员工账号自动关联/创建报工员工（登录打卡自动带出本人）；员工档案（分组/电话）在 报工设置-员工 维护" />

    <div class="toolbar">
      <el-input v-model="kw" placeholder="搜索用户名/姓名" clearable size="small" style="width: 200px" />
      <el-button type="primary" size="small" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
    </div>

    <PaginatedTable v-if="!isMobile" :items="filtered" show-index empty-text="暂无用户" v-loading="loading">
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="displayName" label="姓名" min-width="120" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="tagType(row.role)" size="small">{{ roleName(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分组" width="100">
        <template #default="{ row }">
          <span v-if="row.workEmployeeId">{{ empGroup(row.workEmployeeId) }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="关联员工" width="130">
        <template #default="{ row }">
          <span v-if="row.workEmployeeId">{{ empName(row.workEmployeeId) }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.active ? 'success' : 'danger'" size="small">{{ row.active ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="created_at" label="创建时间" min-width="150" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.active ? 'warning' : 'success'" size="small" @click="toggle(row)">
            {{ row.active ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PaginatedTable>

    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in filtered" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.displayName || row.username }}</span>
          <el-tag :type="tagType(row.role)" size="small">{{ roleName(row.role) }}</el-tag>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>用户名</span><b>{{ row.username }}</b></div>
          <div class="m-row"><span>创建时间</span><b>{{ row.created_at }}</b></div>
        </div>
        <div class="m-actions">
          <el-button link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button>
          <el-button link :type="row.active ? 'warning' : 'success'" size="small" @click.stop="toggle(row)">
            {{ row.active ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
        </div>
      </div>
      <div v-if="!filtered.length" class="m-empty">暂无用户</div>
    </div>

    <!-- 新增/编辑弹窗（destroy-on-close：每次打开重新渲染，杜绝表单残留） -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑用户' : '新增用户'" width="440px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.displayName" placeholder="与报工员工名单同名将自动关联" />
        </el-form-item>
        <el-form-item label="关联员工">
          <el-input :model-value="form.workEmployeeName || (form.id ? '（未匹配到同名报工员工）' : '（员工账号将自动创建报工员工身份）')" disabled size="small" />
          <div class="link-hint">员工账号：按姓名自动关联报工员工；名单无同名则自动创建。关联后登录打卡页自动带出本人；员工分组/电话在 报工设置-员工 维护</div>
        </el-form-item>
        <el-form-item label="密码" :required="!form.id">
          <el-input v-model="form.password" type="password" show-password
                    :placeholder="form.id ? '留空则不修改' : '至少 4 位'" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="员工" value="employee" />
            <el-option label="管理员" value="admin" />
            <el-option label="老板" value="boss" />
            <el-option label="开发" value="dev" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import PaginatedTable from '../components/PaginatedTable.vue'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const editVisible = ref(false)
const kw = ref('')
const form = reactive({ id: null, username: '', displayName: '', password: '', role: 'employee', workEmployeeId: null, workEmployeeName: '' })
const workEmployees = ref([])

// 按关键词过滤（用户名/姓名）
const filtered = computed(() => {
  const k = kw.value.trim().toLowerCase()
  if (!k) return items.value
  return items.value.filter(u =>
    (u.username || '').toLowerCase().includes(k) || (u.displayName || '').toLowerCase().includes(k))
})

function empName(id) {
  const e = workEmployees.value.find(x => x.id === Number(id))
  return e ? e.name : `员工#${id}`
}
function empGroup(id) {
  const e = workEmployees.value.find(x => x.id === Number(id))
  return e ? (e.group_name || '未分组') : '-'
}

const ROLE_NAMES = { admin: '管理员', employee: '员工', boss: '老板', dev: '开发' }
function roleName(r) {
  return ROLE_NAMES[r] || (r || '未设置')
}
function tagType(r) {
  return r === 'admin' ? 'danger' : r === 'boss' ? 'warning' : r === 'dev' ? 'primary' : 'info'
}

async function load() {
  loading.value = true
  try {
    const [u, e] = await Promise.all([
      request.get('/users'),
      request.get('/work/employees'),
    ])
    items.value = u.data.items
    workEmployees.value = e.data.items
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, username: '', displayName: '', password: '', role: 'employee', workEmployeeId: null, workEmployeeName: '' })
  editVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, username: row.username, displayName: row.displayName || '', password: '', role: row.role || 'employee', workEmployeeId: row.workEmployeeId || null, workEmployeeName: row.workEmployeeId ? empName(row.workEmployeeId) : '' })
  editVisible.value = true
}

async function save() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.displayName.trim()) {
    ElMessage.warning('请输入姓名（员工账号将按姓名创建报工员工身份）')
    return
  }
  if (!form.id && form.password.length < 4) {
    ElMessage.warning('密码至少 4 位')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      const payload = { displayName: form.displayName, role: form.role }
      if (form.password) payload.password = form.password
      await request.put(`/users/${form.id}`, payload)
    } else {
      await request.post('/users', { username: form.username, password: form.password, displayName: form.displayName, role: form.role })
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  try {
    await request.put(`/users/${row.id}`, { active: !row.active })
    ElMessage.success(row.active ? '已禁用' : '已启用')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await request.delete(`/users/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => load())
</script>

<style scoped>
.muted { color: #c0c4cc; }
.link-hint { font-size: 11px; color: #909399; line-height: 1.5; margin-top: 2px; }

/* 手机卡片 */
.m-cards { display: flex; flex-direction: column; gap: 10px; }
.m-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 10px 12px; box-shadow: 0 1px 2px rgba(0,0,0,.04);
}
.m-card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.m-name { font-size: 15px; font-weight: 600; color: #303133; }
.m-card-body { display: flex; flex-direction: column; gap: 4px; }
.m-row { display: flex; justify-content: space-between; font-size: 13px; }
.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }
.m-empty { text-align: center; color: #909399; padding: 30px 0; font-size: 13px; }
.m-actions { display: flex; justify-content: flex-end; gap: 4px; margin-top: 6px; }
</style>
