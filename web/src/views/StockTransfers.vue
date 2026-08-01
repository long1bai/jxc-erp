<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <PageHeader title="库存调拨" desc="仓库间调拨物料，调出减库存、调入加库存" />
      </template>

      <div class="search-bar">
        <el-input v-model="kw" placeholder="搜索单号/仓库/备注" clearable size="small" style="width: 200px"
                  @keyup.enter="load(1)" @clear="load(1)" />
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" size="small"
                        range-separator="~" start-placeholder="开始" end-placeholder="结束"
                        style="width: 250px" @change="load(1)" />
        <el-button type="primary" size="small" @click="load(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button type="success" size="small" @click="openCreate"><el-icon><Plus /></el-icon> 新建调拨</el-button>
      </div>

      <el-table :data="items" v-loading="loading" size="small" stripe max-height="480">
        <el-table-column prop="transfer_no" label="调拨单号" min-width="140" />
        <el-table-column label="调拨方向" min-width="180">
          <template #default="{ row }">
            <span>{{ row.from_warehouse_name || '—' }}</span>
            <el-icon size="12" class="tf-arrow"><Right /></el-icon>
            <span>{{ row.to_warehouse_name || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="transfer_date" label="日期" width="100" />
        <el-table-column prop="total_items" label="物料数" width="70" align="right" />
        <el-table-column prop="total_quantity" label="总数量" width="80" align="right" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="created_by" label="经办人" width="80" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无调拨单" :image-size="60" /></template>
      </el-table>

      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                     :page-size="size" :current-page="page" @current-change="load" />

      <!-- 新建调拨弹窗 -->
      <el-dialog v-model="createVisible" title="新建调拨" width="720px">
        <el-form ref="createFormRef" :rules="createRules" label-width="90px" size="small">
          <el-row :gutter="8">
            <el-col :span="12">
              <el-form-item label="调出仓库" prop="fromWarehouseId" required>
                <el-select v-model="createForm.fromWarehouseId" placeholder="选择调出仓库" size="small" style="width: 100%">
                  <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="调入仓库" prop="toWarehouseId" required>
                <el-select v-model="createForm.toWarehouseId" placeholder="选择调入仓库" size="small" style="width: 100%">
                  <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="8">
            <el-col :span="12">
              <el-form-item label="调拨日期">
                <el-date-picker v-model="createForm.transferDate" type="date" value-format="YYYY-MM-DD"
                                size="small" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="备注">
                <el-input v-model="createForm.remark" size="small" placeholder="选填" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="调拨明细" required>
            <div class="items-toolbar">
              <el-input v-model="pickerKw" placeholder="搜索物料添加" clearable size="small" style="width: 200px"
                        @keyup.enter="searchMaterials(1)" @clear="searchMaterials(1)" />
              <el-button size="small" @click="openPicker">＋ 添加物料</el-button>
            </div>
            <el-table :data="createForm.items" size="small" max-height="220">
              <el-table-column prop="material_name" label="物料" min-width="140" show-overflow-tooltip />
              <el-table-column prop="spec" label="规格" min-width="90" show-overflow-tooltip />
              <el-table-column label="数量" width="150">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="0.001" :precision="3" :step="1" size="small" style="width: 120px" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="60">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="createForm.items.splice($index, 1)">删</el-button>
                </template>
              </el-table-column>
              <template #empty><el-empty description="请添加物料" :image-size="40" /></template>
            </el-table>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button size="small" @click="createVisible = false">取消</el-button>
          <el-button size="small" type="primary" @click="save">保存调拨</el-button>
        </template>
      </el-dialog>

      <!-- 物料选择弹窗 -->
      <el-dialog v-model="pickerVisible" title="选择物料" width="640px">
        <div class="search-bar">
          <el-input v-model="pickerKw" placeholder="搜索名称/编号/规格" clearable size="small" style="width: 240px"
                    @keyup.enter="searchMaterials(1)" @clear="searchMaterials(1)" />
          <el-button type="primary" size="small" @click="searchMaterials(1)">搜索</el-button>
        </div>
        <el-table :data="pickerItems" size="small" max-height="340" highlight-current-row
                  @row-dblclick="pickMaterial" @current-change="(r) => pickerCurrent = r">
          <el-table-column prop="code" label="编号" width="100" />
          <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" min-width="100" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="60" />
          <template #empty><el-empty description="无匹配物料" :image-size="50" /></template>
        </el-table>
        <el-pagination class="pager" background layout="prev, pager, next" :total="pickerTotal"
                       :page-size="20" :current-page="pickerPage" @current-change="searchMaterials" />
        <template #footer>
          <el-button size="small" @click="pickerVisible = false">取消</el-button>
          <el-button size="small" type="primary" :disabled="!pickerCurrent" @click="pickMaterial(pickerCurrent)">添加</el-button>
        </template>
      </el-dialog>

      <!-- 详情弹窗 -->
      <el-dialog v-model="detailVisible" title="调拨单详情" width="640px">
        <div v-if="detail" class="m-detail-items">
          <div class="m-di-line"><span>调拨单号</span><b>{{ detail.main.transfer_no }}</b></div>
          <div class="m-di-line"><span>调拨方向</span><b>{{ detail.main.from_warehouse_name }} → {{ detail.main.to_warehouse_name }}</b></div>
          <div class="m-di-line"><span>日期</span><b>{{ detail.main.transfer_date }}</b></div>
          <div class="m-di-line"><span>经办人</span><b>{{ detail.main.created_by || '—' }}</b></div>
          <div class="m-di-line"><span>备注</span><b>{{ detail.main.remark || '—' }}</b></div>
        </div>
        <el-table v-if="detail" :data="detail.items" size="small" max-height="260">
          <el-table-column prop="material_name" label="物料" min-width="150" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" min-width="100" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量" width="90" align="right" />
          <template #empty><el-empty description="无明细" :image-size="40" /></template>
        </el-table>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Plus, Right } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const kw = ref('')
const range = ref(null)

const warehouses = ref([])
const createVisible = ref(false)
const createFormRef = ref(null)
const createForm = reactive({ fromWarehouseId: null, toWarehouseId: null, transferDate: '', remark: '', items: [] })
const createRules = {
  fromWarehouseId: [{ required: true, message: '请选择调出仓库', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择调入仓库', trigger: 'change' }],
}

// 物料选择器
const pickerVisible = ref(false)
const pickerItems = ref([])
const pickerTotal = ref(0)
const pickerPage = ref(1)
const pickerKw = ref('')
const pickerCurrent = ref(null)

const detailVisible = ref(false)
const detail = ref(null)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const params = { kw: kw.value, page: page.value, size: size.value }
    if (range.value && range.value.length === 2) {
      params.start = range.value[0]
      params.end = range.value[1]
    }
    const res = await request.get('/stock/transfers', { params })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  Object.assign(createForm, { fromWarehouseId: null, toWarehouseId: null, transferDate: new Date().toISOString().slice(0, 10), remark: '', items: [] })
  createVisible.value = true
}

async function save() {
  const ok = await createFormRef.value.validate().catch(() => false)
  if (!ok) return
  if (!createForm.items.length) {
    ElMessage.warning('请添加调拨明细')
    return
  }
  try {
    await request.post('/stock/transfers', createForm)
    ElMessage.success('调拨成功')
    createVisible.value = false
    load(1)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openDetail(row) {
  try {
    const res = await request.get(`/stock/transfers/${row.id}`)
    detail.value = res.data
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除调拨单「${row.transfer_no}」？库存将恢复。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await request.delete(`/stock/transfers/${row.id}`)
    ElMessage.success('已删除，库存已恢复')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openPicker() {
  pickerVisible.value = true
  searchMaterials(1)
}

async function searchMaterials(p) {
  if (p) pickerPage.value = p
  try {
    const res = await request.get('/materials', { params: { keyword: pickerKw.value, page: pickerPage.value, size: 20 } })
    pickerItems.value = res.data.items
    pickerTotal.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function pickMaterial(row) {
  if (createForm.items.some((it) => it.material_id === row.id)) {
    ElMessage.warning('该物料已在明细中')
    return
  }
  createForm.items.push({ material_id: row.id, material_name: row.name, spec: row.spec, unit: row.unit, quantity: 1 })
  pickerVisible.value = false
}

onMounted(async () => {
  load()
  try {
    const res = await request.get('/warehouses', { params: { size: 100 } })
    warehouses.value = res.data.items || []
  } catch { /* 忽略 */ }
})
</script>

<style scoped>
.tf-arrow { color: #c0c4cc; margin: 0 4px; }
</style>
