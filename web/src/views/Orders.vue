<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="客户订单">
        <template #icon><Document /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建订单
        </el-button>
      </PageHeader>
    </template>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索单号/客户" clearable style="width: 240px"
                @keyup.enter="load(1)" @clear="load(1)">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
      <el-select v-model="statusFilter" placeholder="状态" clearable size="small" style="width: 130px" @change="load(1)">
        <el-option label="待出货" value="pending" />
        <el-option label="部分出货" value="partial" />
        <el-option label="已出货" value="done" />
        <el-option label="已取消" value="cancelled" />
      </el-select>
    </div>

    <!-- 列表 -->
    <el-table :data="items" v-loading="loading" size="small" stripe @row-click="openDetail" v-if="!isMobile">
      <el-table-column prop="co_no" label="订单号" min-width="170" />
      <el-table-column prop="order_date" label="日期" width="100"  sortable/>
      <el-table-column prop="customer_name" label="客户" min-width="150" show-overflow-tooltip />
      <el-table-column prop="total_quantity" label="订购数量" width="90" align="right"  sortable/>
      <el-table-column prop="delivered_quantity" label="已送" width="80" align="right" />
      <el-table-column label="剩余" width="80" align="right">
        <template #default="{ row }">{{ fmt(Number(row.total_quantity) - Number(row.delivered_quantity)) }}</template>
      </el-table-column>
      <el-table-column prop="total_amount" label="金额" width="100" align="right" sortable>
        <template #default="{ row }">{{ Number(row.total_amount || 0).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'pending'" link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无订单" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card" @click="openDetail(row)">
        <div class="m-card-head">
          <span class="m-name">{{ row.co_no }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>订单号</span><b>{{ row.co_no }}</b></div>
          <div class="m-row"><span>客户</span><b>{{ row.customer_name }}</b></div>
          <div class="m-row"><span>日期</span><b>{{ row.order_date }}</b></div>
          <div class="m-row"><span>订购数量</span><b>{{ row.total_quantity }}</b></div>
          <div class="m-row"><span>已送</span><b>{{ row.delivered_quantity }}</b></div>
          <div class="m-row"><span>金额</span><b>{{ '￥' + (row.total_amount ?? 0) }}</b></div>
        </div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>

    <!-- 分页 -->
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="load" />

    <!-- 新建订单弹窗 -->
    <el-dialog v-model="createVisible" title="新建客户订单" width="820px" top="4vh">
      <el-form ref="createFormRef" :rules="createRules" label-width="80px" size="small">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId" required>
              <el-select v-model="createForm.customerId" filterable placeholder="选择客户" style="width: 100%">
                <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="日期">
              <el-date-picker v-model="createForm.orderDate" type="date" value-format="YYYY-MM-DD"
                              style="width: 100%" placeholder="下单日期" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="备注"><el-input v-model="createForm.remark" /></el-form-item>
          </el-col>
        </el-row>

        <el-table :data="createForm.items" size="small" border>
          <el-table-column label="物料" min-width="220">
            <template #default="{ row }">
              <el-input v-model="row.materialName" readonly placeholder="点击选择物料" @click="openMaterialPicker(row)">
                <template #append>
                  <el-button @click="openMaterialPicker(row)"><el-icon><Search /></el-icon></el-button>
                </template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column prop="spec" label="规格" width="120" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="数量" width="100">
            <template #default="{ row }">
              <el-input v-model="row.quantity" type="number" min="0" @input="calcRow(row)" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">
              <el-input v-model="row.unitPrice" type="number" min="0" @input="calcRow(row)" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">{{ (row.amount || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column width="50">
            <template #default="{ $index }">
              <el-button link type="danger" @click="createForm.items.splice($index, 1)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="items-toolbar">
          <el-button size="small" type="primary" plain @click="addRow">
            <el-icon><Plus /></el-icon> 添加明细
          </el-button>
          <span class="total-text">合计金额：<b>{{ totalAmount.toFixed(2) }}</b></span>
        </div>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存订单</el-button>
      </template>
    </el-dialog>

    <!-- 物料选择弹窗 -->
    <el-dialog v-model="pickerVisible" title="选择物料" width="640px">
      <div class="search-bar">
        <el-input v-model="pickerKeyword" placeholder="搜索名称/编号/规格" clearable size="small"
                  style="width: 240px" @keyup.enter="searchMaterials(1)" @clear="searchMaterials(1)">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button size="small" type="primary" @click="searchMaterials(1)">搜索</el-button>
      </div>
      <el-table :data="pickerItems" size="small" max-height="380" highlight-current-row
                @row-dblclick="pickMaterial" @current-change="(r) => pickerCurrent = r">
        <el-table-column prop="code" label="编号" width="100" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" min-width="110" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="sale_price" label="销售价" width="80" align="right" />
        <template #empty><el-empty description="无匹配物料" :image-size="50" /></template>
      </el-table>
      <el-pagination class="pager" background layout="prev, pager, next" :total="pickerTotal"
                     :page-size="20" :current-page="pickerPage" @current-change="searchMaterials" />
      <template #footer>
        <el-button size="small" @click="pickerVisible = false">取消</el-button>
        <el-button size="small" type="primary" :disabled="!pickerCurrent" @click="pickMaterial(pickerCurrent)">
          选择
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="'订单 ' + (detail.main?.co_no || '')" width="820px">
      <el-descriptions :column="isMobile ? 1 : 3" size="small" border class="mb">
        <el-descriptions-item label="客户">{{ detail.main?.customer_name }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ fmtDate(detail.main?.order_date) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.main?.status)" size="small">{{ statusText(detail.main?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="订购数量">{{ detail.main?.total_quantity }}</el-descriptions-item>
        <el-descriptions-item label="已送数量">{{ detail.main?.delivered_quantity }}</el-descriptions-item>
        <el-descriptions-item label="金额">￥{{ fmtAmount(detail.main?.total_amount) }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="!isMobile" :data="detail.items" size="small" border>
        <el-table-column prop="material_name" label="物料" min-width="160" />
        <el-table-column prop="spec" label="规格" min-width="100" />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="quantity" label="订购量" width="90" align="right" />
        <el-table-column prop="delivered_quantity" label="已送" width="80" align="right" />
        <el-table-column prop="unit_price" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
      </el-table>
      <div v-else class="m-detail-items">
        <div v-for="it in detail.items" :key="it.id" class="m-detail-item">
          <div class="m-di-head">{{ it.material_name }}</div>
          <div class="m-di-spec">{{ it.spec }}</div>
          <div class="m-di-line"><span>订购/已送</span><b>{{ it.quantity }} / {{ it.delivered_quantity }} {{ it.unit }}</b></div>
          <div class="m-di-line"><span>单价</span><b>￥{{ fmtAmount(it.unit_price) }}</b></div>
          <div class="m-di-line"><span>金额</span><b>￥{{ fmtAmount(it.amount) }}</b></div>
        </div>
        <div v-if="!detail.items.length" class="m-empty">无明细</div>
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tradeApi as api } from '../api/trade'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
const createFormRef = ref(null)
const createRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
}
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const customers = ref([])
const createVisible = ref(false)
const createForm = reactive({ customerId: null, orderDate: '', remark: '', items: [] })
const pickerVisible = ref(false)
const pickerKeyword = ref('')
const pickerItems = ref([])
const pickerTotal = ref(0)
const pickerPage = ref(1)
const pickerCurrent = ref(null)
const pickerTarget = ref(null)
const detailVisible = ref(false)
const detail = ref({ main: {}, items: [] })

const totalAmount = computed(() =>
  createForm.items.reduce((s, it) => s + (Number(it.amount) || 0), 0)
)

function statusText(s) {
  return { pending: '待出货', partial: '部分出货', done: '已出货', cancelled: '已取消' }[s] || s
}
function statusType(s) {
  return { pending: 'warning', partial: 'primary', done: 'success', cancelled: 'info' }[s] || ''
}
function fmt(v) {
  return Number(v || 0).toLocaleString()
}
// ISO 日期 → yyyy-MM-dd
function fmtDate(v) {
  if (!v) return '—'
  const d = String(v).slice(0, 10)
  return d || '—'
}
function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const params = { keyword: keyword.value, page: page.value, size: size.value }
    if (statusFilter.value) params.status = statusFilter.value
    const res = await api.orders(params)
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
    const res = await api.customers({ page: 1, size: 100 })
    customers.value = res.data.items
  } catch { /* 忽略 */ }
}

function newRow() {
  return { materialId: null, materialName: '', spec: '', unit: '', quantity: 1, unitPrice: 0, amount: 0 }
}
function addRow() {
  createForm.items.push(newRow())
}
function calcRow(row) {
  row.amount = ((Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)).toFixed(2)
}

function openCreate() {
  Object.assign(createForm, { customerId: null, orderDate: today(), remark: '', items: [newRow()] })
  createVisible.value = true
}

async function save() {
  const ok = await createFormRef.value.validate().catch(() => false)
  if (!ok) return
  const valid = createForm.items.filter((it) => it.materialId && Number(it.quantity) > 0)
  if (!valid.length) {
    ElMessage.warning('请至少添加一条有效明细')
    return
  }
  saving.value = true
  try {
    await api.createOrder({
      customerId: createForm.customerId,
      orderDate: createForm.orderDate,
      remark: createForm.remark,
      items: valid.map((it) => ({
        materialId: it.materialId, materialName: it.materialName, spec: it.spec,
        unit: it.unit, quantity: Number(it.quantity), unitPrice: Number(it.unitPrice) || 0,
      })),
    })
    ElMessage.success('订单已创建')
    createVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除订单「${row.co_no}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteOrder(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openDetail(row) {
  try {
    const res = await api.orderDetail(row.id)
    detail.value = res.data
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// 物料选择器
function openMaterialPicker(row) {
  pickerTarget.value = row
  pickerKeyword.value = ''
  pickerCurrent.value = null
  searchMaterials(1)
  pickerVisible.value = true
}
async function searchMaterials(p) {
  if (p) pickerPage.value = p
  try {
    const res = await api.materials({ keyword: pickerKeyword.value, page: pickerPage.value, size: 20 })
    pickerItems.value = res.data.items
    pickerTotal.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  }
}
function pickMaterial(row) {
  if (!row || !pickerTarget.value) return
  const t = pickerTarget.value
  t.materialId = row.id
  t.materialName = row.name
  t.spec = row.spec || ''
  t.unit = row.unit || ''
  t.unitPrice = row.sale_price || 0
  calcRow(t)
  pickerVisible.value = false
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

onMounted(() => { load(1); loadCustomers() })
</script>

<style scoped>


.items-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.total-text {
  font-size: 13px;
  color: #606266;
}


/* 手机卡片 */






.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }


</style>
