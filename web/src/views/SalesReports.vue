<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="销售报表">
        <template #icon><DataAnalysis /></template>
      </PageHeader>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
              title="统计口径：按送货单日期范围汇总（已删除单据不计）；毛利=销售额-成本（物料进价）；退货金额已自动冲减应收" />

    <FilterBar v-model="range" @query="loadAll" />

    <el-tabs v-model="tab" @tab-change="loadAll" style="margin-top: 4px">
      <!-- ============ 销售统计 ============ -->
      <el-tab-pane label="📊 销售统计" name="stats">
        <div class="stat-block">
          <h4>按客户</h4>
          <el-table :data="stats.customer" size="small" stripe max-height="280">
            <el-table-column prop="customer_name" label="客户" min-width="160" sortable />
            <el-table-column prop="order_count" label="单数" width="80" align="right" sortable />
            <el-table-column prop="total_quantity" label="数量" width="110" align="right" sortable />
            <el-table-column prop="total_amount" label="金额" width="130" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
            </el-table-column>
            <el-table-column label="占比" width="90" align="right">
              <template #default="{ row }">{{ percent(row.total_amount, totalAmt(stats.customer)) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="stat-block">
          <h4>按商品（含毛利）</h4>
          <el-table :data="stats.material" size="small" stripe max-height="280">
            <el-table-column prop="material_name" label="商品" min-width="160" sortable />
            <el-table-column prop="spec" label="规格" min-width="110" />
            <el-table-column prop="total_quantity" label="数量" width="100" align="right" sortable />
            <el-table-column prop="total_amount" label="销售额" width="120" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
            </el-table-column>
            <el-table-column prop="profit" label="毛利" width="110" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.profit) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="stat-block">
          <h4>按仓库</h4>
          <el-table :data="stats.warehouse" size="small" stripe max-height="240">
            <el-table-column prop="warehouse_name" label="仓库" min-width="140" sortable />
            <el-table-column prop="order_count" label="单数" width="80" align="right" sortable />
            <el-table-column prop="total_quantity" label="数量" width="110" align="right" sortable />
            <el-table-column prop="total_amount" label="金额" width="130" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ============ 毛利 ============ -->
      <el-tab-pane label="💰 毛利分析" name="profit">
        <el-table :data="profit.items" size="small" stripe max-height="420">
          <el-table-column prop="customer_name" label="客户" min-width="160" sortable />
          <el-table-column prop="sales" label="销售额" width="120" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.sales) }}</template>
          </el-table-column>
          <el-table-column prop="cost" label="成本" width="120" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.cost) }}</template>
          </el-table-column>
          <el-table-column prop="profit" label="毛利" width="120" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.profit) }}</template>
          </el-table-column>
          <el-table-column prop="margin_rate" label="毛利率" width="100" align="right" sortable>
            <template #default="{ row }">{{ row.margin_rate }}%</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 销售明细 ============ -->
      <el-tab-pane label="📋 销售明细" name="detail">
        <div style="margin-bottom: 10px; display: flex; gap: 8px">
          <el-input v-model="detailKw" placeholder="搜单号/客户/商品" size="small" clearable style="width: 220px"
                    @keyup.enter="loadDetail" @clear="loadDetail" />
          <el-button type="primary" size="small" @click="loadDetail"><el-icon><Search /></el-icon> 查询</el-button>
        </div>
        <PaginatedTable :items="detailItems" show-index empty-text="暂无销售明细" v-loading="detailLoading" :page-size="15">
          <el-table-column prop="dn_no" label="送货单号" min-width="140" sortable />
          <el-table-column prop="dn_date" label="日期" width="100" sortable />
          <el-table-column prop="customer_name" label="客户" min-width="140" sortable />
          <el-table-column prop="material_name" label="商品" min-width="150" sortable />
          <el-table-column prop="spec" label="规格" min-width="110" />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="quantity" label="数量" width="100" align="right" sortable />
          <el-table-column prop="unit_price" label="单价" width="90" align="right" sortable />
          <el-table-column prop="amount" label="金额" width="120" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
          </el-table-column>
        </PaginatedTable>
      </el-tab-pane>

      <!-- ============ 月度分析 ============ -->
      <el-tab-pane label="📈 月度分析" name="monthly">
        <div class="stat-block">
          <h4>销售月度汇总（含退货）</h4>
          <el-table :data="monthly.summary" size="small" stripe max-height="240">
            <el-table-column prop="month" label="月份" width="90" sortable />
            <el-table-column prop="order_count" label="单数" width="80" align="right" sortable />
            <el-table-column prop="total_quantity" label="数量" width="100" align="right" sortable />
            <el-table-column prop="sales" label="销售" width="120" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.sales) }}</template>
            </el-table-column>
            <el-table-column prop="returns_amount" label="退货" width="110" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.returns_amount) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="stat-block">
          <h4>按客户×月</h4>
          <el-table :data="monthly.customer" size="small" stripe max-height="280">
            <el-table-column prop="customer_name" label="客户" min-width="150" sortable />
            <el-table-column prop="month" label="月份" width="90" sortable />
            <el-table-column prop="order_count" label="单数" width="80" align="right" sortable />
            <el-table-column prop="amount" label="金额" width="120" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="stat-block">
          <h4>按商品×月</h4>
          <el-table :data="monthly.material" size="small" stripe max-height="280">
            <el-table-column prop="material_name" label="商品" min-width="150" sortable />
            <el-table-column prop="month" label="月份" width="90" sortable />
            <el-table-column prop="qty" label="数量" width="100" align="right" sortable />
            <el-table-column prop="amount" label="金额" width="120" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ============ 退货统计 ============ -->
      <el-tab-pane label="↩️ 退货统计" name="returns">
        <div class="stat-block">
          <h4>按客户</h4>
          <el-table :data="returns.customer" size="small" stripe max-height="300">
            <el-table-column prop="customer_name" label="客户" min-width="160" sortable />
            <el-table-column prop="return_count" label="退货次数" width="90" align="right" sortable />
            <el-table-column prop="total_quantity" label="数量" width="110" align="right" sortable />
            <el-table-column prop="total_amount" label="金额" width="130" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="stat-block">
          <h4>按商品</h4>
          <el-table :data="returns.material" size="small" stripe max-height="300">
            <el-table-column prop="material_name" label="商品" min-width="160" sortable />
            <el-table-column prop="spec" label="规格" min-width="120" />
            <el-table-column prop="qty" label="数量" width="100" align="right" sortable />
            <el-table-column prop="amount" label="金额" width="130" align="right" sortable>
              <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { DataAnalysis, Search } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'
import FilterBar from '../components/FilterBar.vue'
import PaginatedTable from '../components/PaginatedTable.vue'

const tab = ref('stats')
const range = ref(defaultMonthRange())
const detailKw = ref('')
const detailItems = ref([])
const detailLoading = ref(false)
const stats = reactive({ customer: [], material: [], warehouse: [] })
const profit = reactive({ items: [] })
const monthly = reactive({ summary: [], customer: [], material: [] })
const returns = reactive({ customer: [], material: [] })

function defaultMonthRange() {
  const d = new Date()
  const first = new Date(d.getFullYear(), d.getMonth(), 1)
  const fmt = (x) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  return [fmt(first), fmt(d)]
}

function fmt(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function totalAmt(rows) {
  return rows.reduce((s, r) => s + Number(r.total_amount || 0), 0)
}

function percent(v, total) {
  if (!total) return '-'
  return (Number(v || 0) / total * 100).toFixed(1) + '%'
}

function params() {
  return { from: range.value?.[0] || '', to: range.value?.[1] || '' }
}

async function loadAll() {
  const p = params()
  const [s1, s2, s3, g, m1, m2, m3, r1, r2] = await Promise.all([
    request.get('/sales-stats/by-customer', { params: p }),
    request.get('/sales-stats/by-material', { params: p }),
    request.get('/sales-stats/by-warehouse', { params: p }),
    request.get('/sales-stats/gross-profit', { params: p }),
    request.get('/sales-stats/monthly', { params: p }),
    request.get('/sales-stats/customer-monthly', { params: p }),
    request.get('/sales-stats/material-monthly', { params: p }),
    request.get('/sales-stats/returns/by-customer', { params: p }),
    request.get('/sales-stats/returns/by-material', { params: p }),
  ])
  stats.customer = s1.data.items
  stats.material = s2.data.items
  stats.warehouse = s3.data.items
  profit.items = g.data.items
  monthly.summary = m1.data.items
  monthly.customer = m2.data.items
  monthly.material = m3.data.items
  returns.customer = r1.data.items
  returns.material = r2.data.items
  loadDetail()
}

async function loadDetail() {
  detailLoading.value = true
  try {
    const r = await request.get('/sales-stats/detail', {
      params: { ...params(), kw: detailKw.value || '' },
    })
    detailItems.value = r.data.items
  } finally {
    detailLoading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>

.stat-block {
  margin-bottom: 16px;
}
.stat-block h4 {
  margin: 4px 0 8px;
  font-size: 13px;
  color: #555;
  border-left: 3px solid #409eff;
  padding-left: 8px;
}
</style>
