<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="报表中心">
        <template #icon><DataAnalysis /></template>
      </PageHeader>
    </template>

    <el-tabs v-model="tab">
      <!-- 月度销售 -->
      <el-tab-pane label="📈 月度销售" name="sales">
        <div class="search-bar">
          <el-select v-model="year" size="small" style="width: 110px" @change="loadMonthly">
            <el-option v-for="y in years" :key="y" :label="`${y}年`" :value="y" />
          </el-select>
          <span class="hint">数据源：送货单</span>
        </div>
        <el-table :data="monthly" size="small" stripe highlight-current-row @current-change="selectMonth" max-height="240">
          <el-table-column prop="month" label="月份" width="120"  sortable/>
          <el-table-column prop="doc_count" label="送货单数" width="110" align="right"  sortable/>
          <el-table-column prop="total_quantity" label="总数量" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total_quantity) }}</template>
          </el-table-column>
          <el-table-column prop="total_amount" label="销售金额" align="right" sortable>
            <template #default="{ row }"><b>{{ fmt(row.total_amount) }}</b></template>
          </el-table-column>
          <template #empty><el-empty description="该年暂无数据" :image-size="50" /></template>
        </el-table>

        <div class="sub-title" v-if="monthDetail.length">📄 {{ currentMonth }} 送货明细（{{ monthDetail.length }} 单）</div>
        <el-table :data="monthDetail" size="small" stripe max-height="240">
          <el-table-column prop="dn_no" label="送货单号" min-width="170" />
          <el-table-column prop="dn_date" label="日期" width="100"  sortable/>
          <el-table-column prop="customer_name" label="客户" min-width="150" show-overflow-tooltip />
          <el-table-column prop="total_quantity" label="数量" width="90" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total_quantity) }}</template>
          </el-table-column>
          <el-table-column prop="total_amount" label="金额" width="110" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total_amount) }}</template>
          </el-table-column>
          <template #empty><el-empty description="选择上方月份查看明细" :image-size="50" /></template>
        </el-table>
      </el-tab-pane>

      <!-- 销售对账 -->
      <el-tab-pane label="🤝 销售对账" name="recon-sales">
        <div class="search-bar">
          <el-select v-model="reconSales.partyId" filterable placeholder="选择客户" size="small"
                     style="width: 220px" @change="loadRecon('sales')">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-date-picker v-model="reconSales.range" type="daterange" value-format="YYYY-MM-DD" size="small"
                          range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 250px" />
          <el-button type="primary" size="small" @click="loadRecon('sales')">查询</el-button>
          <el-button v-if="reconSales.partyId" size="small" @click="backRecon('sales')">← 全部客户</el-button>
          <el-button size="small" :disabled="!reconSales.partyId || !reconSales.items.length" @click="printRecon('sales')">🖨 打印对账单</el-button>
        </div>
        <!-- 汇总视图（未选客户）：点行进入该客户明细 -->
        <el-table v-if="!reconSales.partyId" :data="reconSales.items" size="small" stripe max-height="400"
                  highlight-current-row class="recon-summary" @row-click="(row) => { reconSales.partyId = row.party_id; loadRecon('sales') }">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="party_name" label="客户" min-width="220" show-overflow-tooltip />
          <el-table-column prop="bill_count" label="单据数" width="90" align="right"  sortable/>
          <el-table-column prop="total" label="金额" width="130" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total) }}</template>
          </el-table-column>
          <template #empty><el-empty description="该期间暂无销售单据，可调整日期范围" :image-size="50" /></template>
        </el-table>
        <!-- 明细视图（选了客户） -->
        <el-table v-else :data="reconSales.items" size="small" stripe max-height="400">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="dn_no" label="单据编号" min-width="165" />
          <el-table-column prop="dn_date" label="日期" width="100"  sortable/>
          <el-table-column prop="co_no" label="订单号码" min-width="165" />
          <el-table-column prop="material_code" label="商品编码" min-width="110" />
          <el-table-column prop="material_name" label="商品名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" min-width="110" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="quantity" label="数量" width="90" align="right"  sortable/>
          <el-table-column prop="unit_price" label="含税单价" width="90" align="right" />
          <el-table-column prop="amount" label="金额" width="100" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <template #empty><el-empty description="该客户在所选期间无单据" :image-size="50" /></template>
        </el-table>
        <div class="sum-bar" v-if="reconSales.items.length">合计金额：<b>{{ fmt(reconSales.total) }}</b></div>
      </el-tab-pane>

      <!-- 采购对账 -->
      <el-tab-pane label="🤝 采购对账" name="recon-purchase">
        <div class="search-bar">
          <el-select v-model="reconPurchase.partyId" filterable placeholder="选择供应商" size="small"
                     style="width: 220px" @change="loadRecon('purchase')">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <el-date-picker v-model="reconPurchase.range" type="daterange" value-format="YYYY-MM-DD" size="small"
                          range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 250px" />
          <el-button type="primary" size="small" @click="loadRecon('purchase')">查询</el-button>
          <el-button v-if="reconPurchase.partyId" size="small" @click="backRecon('purchase')">← 全部供应商</el-button>
          <el-button size="small" :disabled="!reconPurchase.partyId || !reconPurchase.items.length" @click="printRecon('purchase')">🖨 打印对账单</el-button>
        </div>
        <!-- 汇总视图（未选供应商） -->
        <el-table v-if="!reconPurchase.partyId" :data="reconPurchase.items" size="small" stripe max-height="400"
                  highlight-current-row class="recon-summary" @row-click="(row) => { reconPurchase.partyId = row.party_id; loadRecon('purchase') }">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="party_name" label="供应商" min-width="220" show-overflow-tooltip />
          <el-table-column prop="bill_count" label="单据数" width="90" align="right"  sortable/>
          <el-table-column prop="total" label="金额" width="130" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total) }}</template>
          </el-table-column>
          <template #empty><el-empty description="该期间暂无采购单据，可调整日期范围" :image-size="50" /></template>
        </el-table>
        <!-- 明细视图（选了供应商） -->
        <el-table v-else :data="reconPurchase.items" size="small" stripe max-height="400">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="po_no" label="单据编号" min-width="165" />
          <el-table-column prop="po_date" label="日期" width="100"  sortable/>
          <el-table-column prop="material_code" label="商品编码" min-width="110" />
          <el-table-column prop="material_name" label="商品名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" min-width="110" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="quantity" label="数量" width="90" align="right"  sortable/>
          <el-table-column prop="unit_price" label="含税单价" width="90" align="right" />
          <el-table-column prop="amount" label="金额" width="100" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <template #empty><el-empty description="该供应商在所选期间无单据" :image-size="50" /></template>
        </el-table>
        <div class="sum-bar" v-if="reconPurchase.items.length">合计金额：<b>{{ fmt(reconPurchase.total) }}</b></div>
      </el-tab-pane>

      <!-- 利润分析 -->
      <el-tab-pane label="💹 利润分析" name="profit">
        <div class="search-bar">
          <el-date-picker v-model="profitRange" type="daterange" value-format="YYYY-MM-DD" size="small"
                          range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 250px" />
          <el-button type="primary" size="small" @click="loadProfit">查询</el-button>
        </div>
        <div class="profit-cards" v-if="profitSummary">
          <div class="p-card"><div class="p-label">销售额</div><div class="p-val">{{ fmt(profitSummary.sales) }}</div></div>
          <div class="p-card"><div class="p-label">销售成本</div><div class="p-val">{{ fmt(profitSummary.cost) }}</div></div>
          <div class="p-card"><div class="p-label">毛利</div><div class="p-val" :class="Number(profitSummary.profit) >= 0 ? 'green' : 'red'">{{ fmt(profitSummary.profit) }}</div></div>
          <div class="p-card"><div class="p-label">毛利率</div><div class="p-val">{{ rate }}</div></div>
        </div>
        <el-table :data="profitItems" size="small" stripe max-height="360">
          <el-table-column prop="customer_name" label="客户" min-width="180" show-overflow-tooltip />
          <el-table-column prop="sales" label="销售额" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.sales) }}</template>
          </el-table-column>
          <el-table-column prop="cost" label="成本" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.cost) }}</template>
          </el-table-column>
          <el-table-column prop="profit" label="毛利" align="right" sortable>
            <template #default="{ row }"><b :class="Number(row.profit) >= 0 ? 'green' : 'red'">{{ fmt(row.profit) }}</b></template>
          </el-table-column>
          <el-table-column label="毛利率" align="right">
            <template #default="{ row }">{{ rateOf(row) }}</template>
          </el-table-column>
          <template #empty><el-empty description="选择期间查询" :image-size="50" /></template>
        </el-table>
        <div class="hint">成本按物料档案的采购价计算；未录采购价的物料成本计 0，可在物料管理中补充。</div>
      </el-tab-pane>

      <!-- 销售查询 -->
      <el-tab-pane label="🔍 销售查询" name="sales-query">
        <div class="search-bar">
          <el-select v-model="sq.materialId" filterable clearable placeholder="按物料筛选" size="small" style="width: 220px">
            <el-option v-for="m in sqMaterials" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
          </el-select>
          <el-select v-model="sq.customerId" filterable clearable placeholder="按客户筛选" size="small" style="width: 200px">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-date-picker v-model="sq.range" type="daterange" value-format="YYYY-MM-DD" size="small"
                          range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 250px" />
          <el-button type="primary" size="small" @click="loadSalesQuery">查询</el-button>
        </div>
        <el-table :data="sqItems" size="small" stripe max-height="400">
          <el-table-column prop="dn_no" label="送货单号" min-width="165" />
          <el-table-column prop="dn_date" label="日期" width="100"  sortable/>
          <el-table-column prop="customer_name" label="客户" min-width="150" show-overflow-tooltip />
          <el-table-column prop="material_code" label="编码" min-width="110" />
          <el-table-column prop="material_name" label="名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" min-width="100" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量" width="90" align="right"  sortable/>
          <el-table-column prop="unit_price" label="单价" width="90" align="right" />
          <el-table-column prop="amount" label="金额" width="100" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <template #empty><el-empty description="选择条件查询" :image-size="50" /></template>
        </el-table>
        <div class="sum-bar" v-if="sqItems.length">共 {{ sqItems.length }} 条，合计 <b>{{ fmt(sqTotal) }}</b></div>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const tab = ref('sales')
const year = ref(new Date().getFullYear())
const years = computed(() => {
  const cur = new Date().getFullYear()
  return Array.from({ length: 8 }, (_, i) => cur - i)
})
const monthly = ref([])
const monthDetail = ref([])
const currentMonth = ref('')
const customers = ref([])
const suppliers = ref([])
const reconSales = reactive({ partyId: null, range: defaultMonthRange(), items: [], total: 0 })
const reconPurchase = reactive({ partyId: null, range: defaultMonthRange(), items: [], total: 0 })
const profitRange = ref(defaultMonthRange())
const profitSummary = ref(null)
const profitItems = ref([])
const sq = reactive({ materialId: null, customerId: null, range: defaultMonthRange() })
const sqMaterials = ref([])
const sqItems = ref([])
const sqTotal = ref(0)

const rate = computed(() => {
  if (!profitSummary.value || Number(profitSummary.value.sales) === 0) return '-'
  return (Number(profitSummary.value.profit) / Number(profitSummary.value.sales) * 100).toFixed(1) + '%'
})
function rateOf(row) {
  if (Number(row.sales) === 0) return '-'
  return (Number(row.profit) / Number(row.sales) * 100).toFixed(1) + '%'
}

function fmt(v) {
  return Number(v || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 本月 1 号 ~ 今天（利润/销售查询/对账的默认范围）
function defaultMonthRange() {
  const now = new Date()
  const first = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  return [first, today]
}

async function loadMonthly() {
  const res = await request.get('/reports/sales-monthly', { params: { year: year.value } })
  monthly.value = res.data.items
  if (monthly.value.length) {
    selectMonth({ row: monthly.value[monthly.value.length - 1] })
  } else {
    monthDetail.value = []
  }
}
async function selectMonth(row) {
  if (!row || !row.month) return
  currentMonth.value = row.month
  const res = await request.get('/reports/sales-detail', { params: { month: row.month } })
  monthDetail.value = res.data.items
}

async function loadRecon(type) {
  const rc = type === 'sales' ? reconSales : reconPurchase
  if (!rc.range || rc.range.length !== 2) {
    ElMessage.warning('请选择日期范围')
    return
  }
  const res = await request.get('/reports/reconciliation', {
    params: { type, partyId: rc.partyId || undefined, start: rc.range[0], end: rc.range[1] },
  })
  rc.items = res.data.items
  rc.total = Number(res.data.total)
}

// 从明细返回汇总视图
function backRecon(type) {
  const rc = type === 'sales' ? reconSales : reconPurchase
  rc.partyId = null
  rc.items = []
  rc.total = 0
  loadRecon(type)
}

function printRecon(type) {
  const rc = type === 'sales' ? reconSales : reconPurchase
  const q = { type, partyId: rc.partyId, start: rc.range[0], end: rc.range[1] }
  const url = router.resolve({ path: '/reports/reconciliation-print', query: q }).href
  window.open(url, '_blank', 'width=1050,height=800')
}

async function loadProfit() {
  if (!profitRange.value || profitRange.value.length !== 2) {
    ElMessage.warning('请选择日期范围')
    return
  }
  const res = await request.get('/reports/profit', { params: { start: profitRange.value[0], end: profitRange.value[1] } })
  profitSummary.value = res.data.summary
  profitItems.value = res.data.items
}

async function loadSalesQuery() {
  const params = {}
  if (sq.materialId) params.materialId = sq.materialId
  if (sq.customerId) params.customerId = sq.customerId
  if (sq.range && sq.range.length === 2) {
    params.start = sq.range[0]
    params.end = sq.range[1]
  }
  if (!params.start && !params.materialId && !params.customerId) {
    ElMessage.warning('请至少选择一个筛选条件或日期范围')
    return
  }
  const res = await request.get('/reports/sales-query', { params })
  sqItems.value = res.data.items
  sqTotal.value = sqItems.value.reduce((s, r) => s + Number(r.amount || 0), 0)
}

onMounted(async () => {
  loadMonthly()
  loadProfit()
  loadSalesQuery()
  const [c, s, m] = await Promise.all([
    request.get('/customers', { params: { page: 1, size: 100 } }),
    request.get('/suppliers', { params: { page: 1, size: 100 } }),
    request.get('/materials', { params: { page: 1, size: 100 } }),
  ])
  customers.value = c.data.items
  suppliers.value = s.data.items
  sqMaterials.value = m.data.items
})
</script>

<style scoped>
.search-bar { display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; align-items: center; }
.hint { font-size: 12px; color: #909399; }
.sub-title { font-size: 13px; font-weight: 600; color: #303133; margin: 12px 0 6px; }
.sum-bar { margin-top: 10px; font-size: 13px; color: #606266; text-align: right; }
.recon-summary :deep(.el-table__row) { cursor: pointer; }
.profit-cards { display: flex; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.p-card { flex: 1; min-width: 140px; background: #f7f8fa; border-radius: 6px; padding: 12px; text-align: center; }
.p-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.p-val { font-size: 17px; font-weight: 700; color: #303133; }
.green { color: #67c23a !important; }
.red { color: #f56c6c !important; }
.hint { margin-top: 8px; font-size: 12px; color: #909399; }
</style>
