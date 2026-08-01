<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="报工基础资料">
        <template #icon><Setting /></template>
      </PageHeader>
    </template>

    <el-tabs v-model="tab">
      <!-- 分组 -->
      <el-tab-pane label="👥 分组" name="groups">
        <div class="toolbar">
          <el-button type="primary" size="small" @click="openGroup()"><el-icon><Plus /></el-icon> 新增分组</el-button>
        </div>
        <el-table :data="groups" size="small" stripe>
          <el-table-column prop="name" label="分组名称" min-width="150" />
          <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openGroup(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeGroup(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无分组" :image-size="60" /></template>
        </el-table>
      </el-tab-pane>

      <!-- 员工（车间工人档案；与登录账号按姓名自动联动） -->
      <el-tab-pane label="👤 员工" name="employees">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
                  title="车间工人档案：新增员工时若与登录账号同名会自动绑定；登录打卡自动带出本人。登录账号（密码/角色/禁用）在 系统-用户管理 维护" />
        <div class="toolbar">
          <el-input v-model="empKw" placeholder="搜索姓名/工号" clearable size="small" style="width: 180px"
                    @keyup.enter="loadEmployees" @clear="loadEmployees" />
          <el-button type="primary" size="small" @click="loadEmployees"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button type="success" size="small" @click="openEmployee()"><el-icon><Plus /></el-icon> 新增员工</el-button>
        </div>
        <PaginatedTable :items="employees" show-index empty-text="暂无员工">
          <el-table-column prop="name" label="姓名" width="110" />
          <el-table-column prop="username" label="工号" width="110" />
          <el-table-column prop="group_name" label="分组" width="100" />
          <el-table-column prop="phone" label="电话" width="130" />
          <el-table-column prop="role" label="角色" width="90" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEmployee(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeEmployee(row)">删除</el-button>
            </template>
          </el-table-column>
        </PaginatedTable>
      </el-tab-pane>

      <!-- 工序 -->
      <el-tab-pane label="⚙️ 工序" name="processes">
        <div class="toolbar">
          <el-button type="primary" size="small" @click="openProcess()"><el-icon><Plus /></el-icon> 新增工序</el-button>
        </div>
        <el-table :data="processes" size="small" stripe max-height="460">
          <el-table-column prop="name" label="工序名称" min-width="140" />
          <el-table-column prop="group_name" label="分组" width="100" />
          <el-table-column prop="unit_price" label="单价" width="90" align="right" />
          <el-table-column prop="description" label="说明" min-width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openProcess(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeProcess(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无工序" :image-size="60" /></template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 分组弹窗 -->
    <el-dialog v-model="groupVisible" :title="groupForm.id ? '编辑分组' : '新增分组'" width="420px">
      <el-form label-width="80px" size="small">
        <el-form-item label="名称" required><el-input v-model="groupForm.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="groupForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="groupVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <!-- 员工弹窗 -->
    <el-dialog v-model="empVisible" :title="empForm.id ? '编辑员工' : '新增员工'" width="460px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item label="姓名" required><el-input v-model="empForm.name" /></el-form-item>
        <el-form-item label="工号"><el-input v-model="empForm.username" placeholder="留空自动生成（W001 起）" /></el-form-item>
        <el-form-item label="分组">
          <el-select v-model="empForm.groupId" clearable placeholder="选择分组" style="width: 100%">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话"><el-input v-model="empForm.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="empVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="saveEmployee">保存</el-button>
      </template>
    </el-dialog>

    <!-- 工序弹窗 -->
    <el-dialog v-model="procVisible" :title="procForm.id ? '编辑工序' : '新增工序'" width="460px">
      <el-form label-width="80px" size="small">
        <el-form-item label="名称" required><el-input v-model="procForm.name" /></el-form-item>
        <el-form-item label="分组">
          <el-select v-model="procForm.groupId" clearable placeholder="选择分组" style="width: 100%">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价"><el-input-number v-model="procForm.unitPrice" :min="0" :precision="3" style="width: 180px" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="procForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="procVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="saveProcess">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import PaginatedTable from '../components/PaginatedTable.vue'

const tab = ref('groups')
const groups = ref([])
const employees = ref([])
const processes = ref([])
const empKw = ref('')
const groupVisible = ref(false)
const groupForm = reactive({ id: null, name: '', description: '' })
const empVisible = ref(false)
const empForm = reactive({ id: null, name: '', username: '', groupId: null, phone: '' })
const procVisible = ref(false)
const procForm = reactive({ id: null, name: '', groupId: null, unitPrice: 0, description: '' })

async function loadGroups() {
  const res = await request.get('/work/groups')
  groups.value = res.data.items
}
async function loadEmployees() {
  const res = await request.get('/work/employees', { params: { keyword: empKw.value } })
  employees.value = res.data.items
}
async function loadProcesses() {
  const res = await request.get('/work/processes')
  processes.value = res.data.items
}

function openGroup(row) {
  Object.assign(groupForm, row ? { id: row.id, name: row.name, description: row.description || '' } : { id: null, name: '', description: '' })
  groupVisible.value = true
}
async function saveGroup() {
  if (!groupForm.name.trim()) { ElMessage.warning('请输入分组名称'); return }
  try {
    if (groupForm.id) await request.put(`/work/groups/${groupForm.id}`, groupForm)
    else await request.post('/work/groups', groupForm)
    ElMessage.success('保存成功')
    groupVisible.value = false
    loadGroups()
  } catch (e) { ElMessage.error(e.message) }
}
async function removeGroup(row) {
  try { await ElMessageBox.confirm(`确定删除分组「${row.name}」？`, '删除确认', { type: 'warning' }) } catch { return }
  try { await request.delete(`/work/groups/${row.id}`); ElMessage.success('已删除'); loadGroups() }
  catch (e) { ElMessage.error(e.message) }
}

function openEmployee(row) {
  Object.assign(empForm, row ? { id: row.id, name: row.name, username: row.username || '', groupId: row.group_id || null, phone: row.phone || '' } : { id: null, name: '', username: '', groupId: null, phone: '' })
  empVisible.value = true
}
async function saveEmployee() {
  if (!empForm.name.trim()) { ElMessage.warning('请输入姓名'); return }
  try {
    if (empForm.id) await request.put(`/work/employees/${empForm.id}`, empForm)
    else await request.post('/work/employees', empForm)
    ElMessage.success('保存成功（与账号同名将自动绑定）')
    empVisible.value = false
    loadEmployees()
  } catch (e) { ElMessage.error(e.message) }
}
async function removeEmployee(row) {
  try { await ElMessageBox.confirm(`确定删除员工「${row.name}」？`, '删除确认', { type: 'warning' }) } catch { return }
  try { await request.delete(`/work/employees/${row.id}`); ElMessage.success('已删除'); loadEmployees() }
  catch (e) { ElMessage.error(e.message) }
}

function openProcess(row) {
  Object.assign(procForm, row ? { id: row.id, name: row.name, groupId: row.group_id || null, unitPrice: Number(row.unit_price || 0), description: row.description || '' } : { id: null, name: '', groupId: null, unitPrice: 0, description: '' })
  procVisible.value = true
}
async function saveProcess() {
  if (!procForm.name.trim()) { ElMessage.warning('请输入工序名称'); return }
  try {
    if (procForm.id) await request.put(`/work/processes/${procForm.id}`, procForm)
    else await request.post('/work/processes', procForm)
    ElMessage.success('保存成功')
    procVisible.value = false
    loadProcesses()
  } catch (e) { ElMessage.error(e.message) }
}
async function removeProcess(row) {
  try { await ElMessageBox.confirm(`确定删除工序「${row.name}」？`, '删除确认', { type: 'warning' }) } catch { return }
  try { await request.delete(`/work/processes/${row.id}`); ElMessage.success('已删除'); loadProcesses() }
  catch (e) { ElMessage.error(e.message) }
}

onMounted(() => { loadGroups(); loadEmployees(); loadProcesses() })
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 10px; }
</style>
