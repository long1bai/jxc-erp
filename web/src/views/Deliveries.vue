<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="送货单（销售出库）">
        <template #icon><Van /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建送货单
        </el-button>
      </PageHeader>
    </template>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索单号/客户/订单号" clearable style="width: 240px"
                @keyup.enter="load(1)" @clear="load(1)">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="items" v-loading="loading" size="small" stripe @row-click="openDetail" v-if="!isMobile">
      <el-table-column prop="dn_no" label="送货单号" min-width="180" />
      <el-table-column prop="dn_date" label="日期" width="110"  sortable/>
      <el-table-column prop="customer_name" label="客户" min-width="150" show-overflow-tooltip />
      <el-table-column prop="co_no" label="关联订单" min-width="150">
        <template #default="{ row }">{{ row.co_no || '—' }}</template>
      </el-table-column>
      <el-table-column prop="total_quantity" label="总数量" width="90" align="right"  sortable/>
      <el-table-column prop="total_amount" label="总金额" width="110" align="right" sortable>
        <template #default="{ row }">{{ Number(row.total_amount || 0).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="printRow(row)">打印</el-button>
          <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无送货单" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card" @click="openDetail(row)">
        <div class="m-card-head">
          <span class="m-name">{{ row.dn_no }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>送货单号</span><b>{{ row.dn_no }}</b></div>
          <div class="m-row"><span>客户</span><b>{{ row.customer_name }}</b></div>
          <div class="m-row"><span>日期</span><b>{{ row.dn_date }}</b></div>
          <div class="m-row"><span>关联订单</span><b>{{ row.co_no }}</b></div>
          <div class="m-row"><span>总数量</span><b>{{ row.total_quantity }}</b></div>
          <div class="m-row"><span>总金额</span><b>{{ '￥' + (row.total_amount ?? 0) }}</b></div>
          <div class="m-row"><span>备注</span><b>{{ row.remark }}</b></div>
        </div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>

    <!-- 分页 -->
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="load" />

    <!-- 新建送货单弹窗 -->
    <el-dialog v-model="createVisible" title="新建送货单" width="820px" top="3vh">
      <el-form label-width="80px" size="small">
        <el-row :gutter="10">
          <el-col :span="9">
            <el-form-item label="客户" required>
              <el-select v-model="createForm.customerId" filterable placeholder="选择客户" style="width: 100%"
                         @change="onCustomerChange">
                <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="9">
            <el-form-item label="关联订单">
              <el-select v-model="createForm.orderId" filterable clearable placeholder="不选=临时出货" style="width: 100%"
                         @change="onOrderChange">
                <el-option v-for="o in openOrders" :key="o.id" :label="`${o.co_no}（剩 ${fmt(o.total_quantity - o.delivered_quantity)}）`" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="日期">
              <el-date-picker v-model="createForm.deliveryDate" type="date" value-format="YYYY-MM-DD"
                              style="width: 100%" placeholder="送货日期" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" class="remark-item">
          <el-input v-model="createForm.remark" placeholder="可选" />
        </el-form-item>

        <el-table :data="createForm.items" size="small" border>
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">
              <el-input v-model="row.materialName" readonly placeholder="点击选择物料" @click="openMaterialPicker(row)">
                <template #append>
                  <el-button @click="openMaterialPicker(row)"><el-icon><Search /></el-icon></el-button>
                </template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column prop="spec" label="规格" width="110" />
          <el-table-column prop="unit" label="单位" width="60" />
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
          <el-table-column label="金额" width="90" align="right">
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
          <div>
            <el-button size="small" type="primary" plain @click="addRow">
              <el-icon><Plus /></el-icon> 添加明细
            </el-button>
            <span class="hint" v-if="createForm.orderId">已关联订单，出货量不能超过订单剩余量</span>
          </div>
          <span class="total-text">合计金额：<b>{{ totalAmount.toFixed(2) }}</b></span>
        </div>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">确认出货</el-button>
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
    <el-dialog v-model="detailVisible" :title="'送货单 ' + (detail.main?.dn_no || '')" width="720px">
      <el-descriptions :column="isMobile ? 1 : 3" size="small" border class="mb">
        <el-descriptions-item label="客户">{{ detail.main?.customer_name }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ fmtDate(detail.main?.dn_date) }}</el-descriptions-item>
        <el-descriptions-item label="关联订单">{{ detail.main?.co_no || '—' }}</el-descriptions-item>
        <el-descriptions-item label="总数量">{{ detail.main?.total_quantity }}</el-descriptions-item>
        <el-descriptions-item label="总金额">￥{{ fmtAmount(detail.main?.total_amount) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.main?.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="!isMobile" :data="detail.items" size="small" border>
        <el-table-column prop="material_name" label="物料" min-width="160" />
        <el-table-column prop="spec" label="规格" min-width="100" />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unit_price" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
      </el-table>
      <div v-else class="m-detail-items">
        <div v-for="it in detail.items" :key="it.id" class="m-detail-item">
          <div class="m-di-head">{{ it.material_name }}</div>
          <div class="m-di-spec">{{ it.spec }}</div>
          <div class="m-di-line"><span>数量</span><b>{{ it.quantity }} {{ it.unit }}</b></div>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tradeApi as api } from '../api/trade'

const router = useRouter()

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const customers = ref([])
const openOrders = ref([])
const createVisible = ref(false)
const createForm = reactive({ customerId: null, orderId: null, deliveryDate: '', remark: '', items: [] })
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
function fmt(v) {
  return v === null || v === undefined ? '0' : Number(v).toLocaleString()
}

// ISO 日期 → yyyy-MM-dd（如 2026-07-23T16:00:00.000Z → 2026-07-23）
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
    const res = await api.deliveries({
      params: { keyword: keyword.value, page: page.value, size: size.value },
    })
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
  Object.assign(createForm, { customerId: null, orderId: null, deliveryDate: today(), remark: '', items: [newRow()] })
  openOrders.value = []
  createVisible.value = true
}

async function onCustomerChange(cid) {
  createForm.orderId = null
  createForm.items = [newRow()]
  if (!cid) return
  try {
    const res = await api.openOrders(cid)
    openOrders.value = res.data.items
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function onOrderChange(oid) {
  createForm.items = []
  if (!oid) {
    createForm.items = [newRow()]
    return
  }
  try {
    const res = await api.orderDetail(oid)
    const items = res.data.items
    createForm.items = items.map((it) => ({
      materialId: it.material_id,
      materialName: it.material_name,
      spec: it.spec || '',
      unit: it.unit || '',
      quantity: Math.max(Number(it.quantity) - Number(it.delivered_quantity), 0),
      unitPrice: Number(it.unit_price) || 0,
      amount: 0,
    })).filter((it) => it.quantity > 0)
    createForm.items.forEach(calcRow)
    if (!createForm.items.length) {
      ElMessage.info('该订单已全部出货')
      createForm.items = [newRow()]
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function save() {
  if (!createForm.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  const valid = createForm.items.filter((it) => it.materialId && Number(it.quantity) > 0)
  if (!valid.length) {
    ElMessage.warning('请至少添加一条有效明细')
    return
  }
  saving.value = true
  try {
    await api.createDelivery({
      customerId: createForm.customerId,
      orderId: createForm.orderId || null,
      deliveryDate: createForm.deliveryDate,
      remark: createForm.remark,
      items: valid.map((it) => ({
        materialId: it.materialId, materialName: it.materialName, spec: it.spec,
        unit: it.unit, quantity: Number(it.quantity), unitPrice: Number(it.unitPrice) || 0,
      })),
    })
    ElMessage.success('出货成功')
    createVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function printRow(row) {
  const url = router.resolve({ path: `/deliveries/print/${row.id}` }).href
  window.open(url, '_blank', 'width=1000,height=760')
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除送货单「${row.dn_no}」？库存将同步加回。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteDelivery(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openDetail(row) {
  try {
    const res = await api.deliveryDetail(row.id)
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
    const res = await api.materials({
      params: { keyword: pickerKeyword.value, page: pickerPage.value, size: 20 },
    })
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
.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.items-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  flex-wrap: wrap;
  gap: 6px;
}
.total-text {
  font-size: 13px;
  color: #606266;
}
.hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
.mb {
  margin-bottom: 10px;
}
.remark-item {
  margin-bottom: 8px;
}

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
