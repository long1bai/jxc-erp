<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="采购退货">
        <template #icon><RefreshLeft /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建退货单
        </el-button>
      </PageHeader>
    </template>

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索单号/供应商" clearable size="small" style="width: 220px"
                @keyup.enter="load(1)" @clear="load(1)" />
      <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
    </div>

    <el-table :data="items" size="small" stripe v-loading="loading" max-height="480" v-if="!isMobile">
      <el-table-column prop="pr_no" label="退货单号" min-width="170" />
      <el-table-column prop="return_date" label="日期" width="100"  sortable/>
      <el-table-column prop="supplier_name" label="供应商" min-width="160" show-overflow-tooltip />
      <el-table-column prop="total_quantity" label="数量" width="90" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.total_quantity) }}</template>
      </el-table-column>
      <el-table-column prop="total_amount" label="金额" width="110" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.total_amount) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="view(row)">明细</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无退货单" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.pr_no }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>退货单号</span><b>{{ row.pr_no }}</b></div>
          <div class="m-row"><span>供应商</span><b>{{ row.supplier_name }}</b></div>
          <div class="m-row"><span>日期</span><b>{{ row.return_date }}</b></div>
          <div class="m-row"><span>数量</span><b>{{ row.total_quantity }}</b></div>
          <div class="m-row"><span>金额</span><b>{{ '￥' + (row.total_amount ?? 0) }}</b></div>
          <div class="m-row"><span>备注</span><b>{{ row.remark }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="view(row)">{{ 明细 }}</el-button><el-button link type="primary" size="small" @click.stop="remove(row)">{{ 删除 }}</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="load" />

    <!-- 新建弹窗 -->
    <el-dialog v-model="createVisible" title="新建退货单" width="760px">
      <el-form label-width="80px" size="small">
        <el-form-item label="供应商" required>
          <el-select v-model="form.supplierId" filterable placeholder="选择供应商" style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
        </el-form-item>
        <el-form-item label="明细">
          <el-table :data="form.items" size="small" border max-height="280">
            <el-table-column label="物料" min-width="220">
              <template #default="{ row }">
                <el-select v-model="row.materialId" filterable placeholder="选择物料" style="width: 100%" size="small">
                  <el-option v-for="m in materials" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0.001" :precision="3" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="单价" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row.unitPrice" :min="0" :precision="3" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="" width="60">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="form.items.splice($index, 1)">✕</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <div class="row-add">
          <el-button size="small" @click="form.items.push({ materialId: null, quantity: 1, unitPrice: 0 })">
            <el-icon><Plus /></el-icon> 添加明细
          </el-button>
          <span class="hint" v-if="form.items.length">合计：{{ fmt(sum) }} 元</span>
        </div>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">确认退货</el-button>
      </template>
    </el-dialog>

    <!-- 明细弹窗 -->
    <el-dialog v-model="detailVisible" title="退货单明细" width="640px">
      <el-descriptions :column="3" size="small" border class="mb">
        <el-descriptions-item label="单号">{{ detail.main?.pr_no }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.main?.return_date }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.main?.supplier_name }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ fmt(detail.main?.total_quantity) }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detail.main?.total_amount) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.main?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.items" size="small" border>
        <el-table-column prop="material_name" label="物料" min-width="160" />
        <el-table-column prop="spec" label="规格" min-width="120" />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unit_price" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const suppliers = ref([])
const materials = ref([])
const createVisible = ref(false)
const detailVisible = ref(false)
const detail = reactive({ main: null, items: [] })
const form = reactive({ supplierId: null, returnDate: today(), remark: '', items: [] })

const sum = computed(() => form.items.reduce((s, r) => s + (r.quantity || 0) * (r.unitPrice || 0), 0))

function fmt(v) {
  return Number(v || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await request.get('/purchase-returns', { params: { keyword: keyword.value, page: page.value, size: size.value } })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  Object.assign(form, { supplierId: null, returnDate: today(), remark: '', items: [{ materialId: null, quantity: 1, unitPrice: 0 }] })
  try {
    const [s, m] = await Promise.all([
      request.get('/suppliers', { params: { page: 1, size: 100 } }),
      request.get('/materials', { params: { page: 1, size: 100 } }),
    ])
    suppliers.value = s.data.items
    materials.value = m.data.items
  } catch { /* 忽略 */ }
  createVisible.value = true
}

async function save() {
  if (!form.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  const rows = form.items.filter((r) => r.materialId && r.quantity > 0)
  if (!rows.length) {
    ElMessage.warning('请添加退货明细')
    return
  }
  const payload = rows.map((r) => {
    const m = materials.value.find((x) => x.id === r.materialId)
    return { materialId: r.materialId, materialName: m ? m.name : '', spec: m ? m.spec : '', unit: m ? m.unit : '', quantity: r.quantity, unitPrice: r.unitPrice }
  })
  saving.value = true
  try {
    await request.post('/purchase-returns', { supplierId: form.supplierId, returnDate: form.returnDate, remark: form.remark, items: payload })
    ElMessage.success('退货成功，库存已扣减')
    createVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function view(row) {
  const res = await request.get(`/purchase-returns/${row.id}`)
  detail.main = res.data.main
  detail.items = res.data.items
  detailVisible.value = true
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除退货单「${row.pr_no}」？库存将同步加回。`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await request.delete(`/purchase-returns/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

onMounted(() => load(1))
</script>

<style scoped>
.search-bar { display: flex; gap: 8px; margin-bottom: 10px; }
.pager { margin-top: 10px; justify-content: flex-end; }
.row-add { text-align: center; margin-bottom: 12px; }
.hint { margin-left: 12px; font-size: 13px; color: #606266; }
.mb { margin-bottom: 10px; }

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
