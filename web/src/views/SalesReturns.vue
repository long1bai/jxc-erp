<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="销售退货">
        <template #icon><RefreshLeft /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建退货单
        </el-button>
      </PageHeader>
    </template>

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索单号/客户" clearable size="small" style="width: 220px"
                @keyup.enter="load(1)" @clear="load(1)" />
      <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
    </div>

    <el-table :data="items" size="small" stripe v-loading="loading" max-height="480" v-if="!isMobile">
      <el-table-column prop="sr_no" label="退货单号" min-width="170" />
      <el-table-column prop="return_date" label="日期" width="100" sortable />
      <el-table-column prop="customer_name" label="客户" min-width="160" show-overflow-tooltip />
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
          <span class="m-name">{{ row.sr_no }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>退货单号</span><b>{{ row.sr_no }}</b></div>
          <div class="m-row"><span>客户</span><b>{{ row.customer_name }}</b></div>
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
    <el-dialog v-model="createVisible" title="新建退货单" width="820px" destroy-on-close>
      <el-form ref="createFormRef" :rules="createRules" label-width="80px" size="small">
        <el-form-item label="客户" prop="customerId" required>
          <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
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
                  <el-option v-for="m in materials" :key="m.id" :label="`${m.code || ''} ${m.name}`" :value="m.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="110">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0" :precision="3" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="单价" width="110">
              <template #default="{ row }">
                <el-input-number v-model="row.unitPrice" :min="0" :precision="4" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">￥{{ fmt((row.quantity || 0) * (row.unitPrice || 0)) }}</template>
            </el-table-column>
            <el-table-column width="60" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="form.items.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="addItem"><el-icon><Plus /></el-icon> 添加明细行</el-button>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 明细查看弹窗 -->
    <el-dialog v-model="viewVisible" title="退货单明细" width="640px">
      <el-descriptions :column="2" size="small" border style="margin-bottom: 10px">
        <el-descriptions-item label="退货单号">{{ viewMain.sr_no }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ viewMain.return_date }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ viewMain.customer_name }}</el-descriptions-item>
        <el-descriptions-item label="金额">￥{{ fmt(viewMain.total_amount) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="viewItems" size="small" border>
        <el-table-column prop="material_name" label="物料" min-width="160" />
        <el-table-column prop="spec" label="规格" min-width="120" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unit_price" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { RefreshLeft, Plus } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
const createFormRef = ref(null)
const createRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
}
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const customers = ref([])
const materials = ref([])
const createVisible = ref(false)
const viewVisible = ref(false)
const viewMain = ref({})
const viewItems = ref([])
const form = reactive({ customerId: null, returnDate: '', remark: '', items: [] })

function fmt(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const r = await request.get('/sales-returns', { params: { keyword: keyword.value, page: page.value, size: size.value } })
    items.value = r.data.items
    total.value = r.data.total
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  form.customerId = null
  form.returnDate = new Date().toISOString().slice(0, 10)
  form.remark = ''
  form.items = []
  addItem()
  createVisible.value = true
}

function addItem() {
  form.items.push({ materialId: null, quantity: 1, unitPrice: 0 })
}

async function save() {
  const ok = await createFormRef.value.validate().catch(() => false)
  if (!ok) return
  if (!form.items.length || form.items.some(i => !i.materialId || !i.quantity)) return alert('请填写退货明细')
  saving.value = true
  try {
    const r = await request.post('/sales-returns', form)
    if (!r.data?.success) return alert(r.data?.error || '保存失败')
    createVisible.value = false
    load(1)
  } finally {
    saving.value = false
  }
}

async function view(row) {
  const r = await request.get(`/sales-returns/${row.id}`)
  viewMain.value = r.data.main
  viewItems.value = r.data.items
  viewVisible.value = true
}

async function remove(row) {
  if (!confirm(`确认删除退货单 ${row.sr_no}？`)) return
  await request.delete(`/sales-returns/${row.id}`)
  load(page.value)
}

onMounted(async () => {
  load(1)
  const [c, m] = await Promise.all([
    request.get('/customers'),
    request.get('/materials?size=5000'),
  ])
  customers.value = c.data.items
  materials.value = m.data.items
})
</script>

<style scoped>



/* 手机卡片 */






.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }


</style>
