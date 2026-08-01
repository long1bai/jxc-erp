<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="采购报表">
        <template #icon><DataAnalysis /></template>
      </PageHeader>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
              title="统计口径：按采购单日期范围汇总（已删除单据不计）；本系统采购单=下单+入库一体（无独立采购订单），故无「订单执行情况」；未录「经手人」字段，故无按经手人统计" />

    <FilterBar v-model="range" @query="loadAll" />

    <el-tabs v-model="tab" @tab-change="loadAll" style="margin-top: 4px">
        <!-- ============ 采购统计 ============ -->
        <el-tab-pane label="📊 采购统计" name="stats">
          <div class="stat-block">
            <h4>按供应商</h4>
            <el-table :data="stats.supplier" size="small" stripe max-height="300">
              <el-table-column prop="supplier_name" label="供应商" min-width="150" sortable />
              <el-table-column prop="order_count" label="单数" width="80" align="right" sortable />
              <el-table-column prop="total_quantity" label="数量" width="110" align="right" sortable />
              <el-table-column prop="total_amount" label="金额" width="130" align="right" sortable>
                <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
              </el-table-column>
              <el-table-column prop="占比" label="占比" width="90" align="right">
                <template #default="{ row }">{{ percent(row.total_amount, totalAmt(stats.supplier)) }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div class="stat-block">
            <h4>按商品</h4>
            <el-table :data="stats.material" size="small" stripe max-height="300">
              <el-table-column prop="material_name" label="商品" min-width="160" sortable />
              <el-table-column prop="spec" label="规格" min-width="120" />
              <el-table-column prop="total_quantity" label="数量" width="100" align="right" sortable />
              <el-table-column prop="total_amount" label="金额" width="130" align="right" sortable>
                <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
              </el-table-column>
              <el-table-column prop="avg_price" label="均价" width="100" align="right" sortable />
            </el-table>
          </div>
          <div class="stat-block">
            <h4>按仓库</h4>
            <el-table :data="stats.warehouse" size="small" stripe max-height="260">
              <el-table-column prop="warehouse_name" label="仓库" min-width="140" sortable />
              <el-table-column prop="order_count" label="单数" width="80" align="right" sortable />
              <el-table-column prop="total_quantity" label="数量" width="110" align="right" sortable />
              <el-table-column prop="total_amount" label="金额" width="130" align="right" sortable>
                <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ============ 采购明细 ============ -->
        <el-tab-pane label="📋 采购明细" name="detail">
          <div style="margin-bottom: 10px; display: flex; gap: 8px">
            <el-input v-model="detailKw" placeholder="搜单号/供应商/商品" size="small" clearable style="width: 220px"
                      @keyup.enter="loadDetail" @clear="loadDetail" />
            <el-button type="primary" size="small" @click="loadDetail"><el-icon><Search /></el-icon> 查询</el-button>
          </div>
          <PaginatedTable :items="detailItems" show-index empty-text="暂无采购明细" v-loading="detailLoading"
                          :page-size="15">
            <el-table-column prop="po_no" label="采购单号" min-width="130" sortable />
            <el-table-column prop="po_date" label="日期" width="100" sortable />
            <el-table-column prop="supplier_name" label="供应商" min-width="140" sortable />
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
            <h4>采购月度汇总</h4>
            <el-table :data="monthly.summary" size="small" stripe max-height="240">
              <el-table-column prop="month" label="月份" width="100" sortable />
              <el-table-column prop="order_count" label="单数" width="80" align="right" sortable />
              <el-table-column prop="total_quantity" label="数量" width="110" align="right" sortable />
              <el-table-column prop="total_amount" label="金额" width="130" align="right" sortable>
                <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div class="stat-block">
            <h4>采购商品月度分析</h4>
            <el-table :data="monthly.material" size="small" stripe max-height="300">
              <el-table-column prop="material_name" label="商品" min-width="150" sortable />
              <el-table-column prop="month" label="月份" width="90" sortable />
              <el-table-column prop="qty" label="数量" width="100" align="right" sortable />
              <el-table-column prop="amount" label="金额" width="130" align="right" sortable>
                <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div class="stat-block">
            <h4>供应商供货月度分析</h4>
            <el-table :data="monthly.supplier" size="small" stripe max-height="300">
              <el-table-column prop="supplier_name" label="供应商" min-width="150" sortable />
              <el-table-column prop="month" label="月份" width="90" sortable />
              <el-table-column prop="order_count" label="单数" width="80" align="right" sortable />
              <el-table-column prop="amount" label="金额" width="130" align="right" sortable>
                <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div class="stat-block">
            <h4>采购价格趋势（商品×月均价）</h4>
            <el-table :data="monthly.trend" size="small" stripe max-height="300">
              <el-table-column prop="material_name" label="商品" min-width="150" sortable />
              <el-table-column prop="month" label="月份" width="90" sortable />
              <el-table-column prop="avg_price" label="均价" width="110" align="right" sortable>
                <template #default="{ row }">￥{{ row.avg_price }}</template>
              </el-table-column>
              <el-table-column prop="qty" label="数量" width="100" align="right" sortable />
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ============ 退货统计 ============ -->
        <el-tab-pane label="↩️ 退货统计" name="returns">
          <div class="stat-block">
            <h4>按供应商</h4>
            <el-table :data="returns.supplier" size="small" stripe max-height="300">
              <el-table-column prop="supplier_name" label="供应商" min-width="150" sortable />
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
const stats = reactive({ supplier: [], material: [], warehouse: [] })
const monthly = reactive({ summary: [], material: [], supplier: [], trend: [] })
const returns = reactive({ supplier: [], material: [] })

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
  const [s1, s2, s3, m1, m2, m3, m4, r1, r2] = await Promise.all([
    request.get('/purchase-stats/by-supplier', { params: p }),
    request.get('/purchase-stats/by-material', { params: p }),
    request.get('/purchase-stats/by-warehouse', { params: p }),
    request.get('/purchase-stats/monthly', { params: p }),
    request.get('/purchase-stats/material-monthly', { params: p }),
    request.get('/purchase-stats/supplier-monthly', { params: p }),
    request.get('/purchase-stats/price-trend', { params: p }),
    request.get('/purchase-stats/returns/by-supplier', { params: p }),
    request.get('/purchase-stats/returns/by-material', { params: p }),
  ])
  stats.supplier = s1.data.items
  stats.material = s2.data.items
  stats.warehouse = s3.data.items
  monthly.summary = m1.data.items
  monthly.material = m2.data.items
  monthly.supplier = m3.data.items
  monthly.trend = m4.data.items
  returns.supplier = r1.data.items
  returns.material = r2.data.items
  loadDetail()
}

async function loadDetail() {
  detailLoading.value = true
  try {
    const r = await request.get('/purchase-stats/detail', {
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
