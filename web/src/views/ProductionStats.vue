<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="生产统计">
        <template #icon><DataAnalysis /></template>
      </PageHeader>
    </template>

    <FilterBar v-model="range" @query="loadAll" />

    <div class="stat-block">
      <h4>成品入库统计（按产品×月）</h4>
      <el-table :data="ins" size="small" stripe max-height="340">
        <el-table-column prop="product_name" label="产品" min-width="170" sortable />
        <el-table-column prop="month" label="月份" width="90" sortable />
        <el-table-column prop="qty" label="数量" width="110" align="right" sortable />
        <el-table-column prop="amount" label="金额" width="130" align="right" sortable>
          <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div class="stat-block">
      <h4>生产退料统计（按物料×月）</h4>
      <el-table :data="returns" size="small" stripe max-height="340">
        <el-table-column prop="material_name" label="物料" min-width="170" sortable />
        <el-table-column prop="month" label="月份" width="90" sortable />
        <el-table-column prop="qty" label="数量" width="110" align="right" sortable />
      </el-table>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { DataAnalysis, Search } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'
import FilterBar from '../components/FilterBar.vue'

const range = ref(defaultMonthRange())
const ins = ref([])
const returns = ref([])

function defaultMonthRange() {
  const d = new Date()
  const first = new Date(d.getFullYear(), d.getMonth(), 1)
  const fmt = (x) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  return [fmt(first), fmt(d)]
}

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadAll() {
  const p = { from: range.value?.[0] || '', to: range.value?.[1] || '' }
  const [a, b] = await Promise.all([
    request.get('/production-stats/ins', { params: p }),
    request.get('/production-stats/returns', { params: p }),
  ])
  ins.value = a.data.items
  returns.value = b.data.items
}

onMounted(loadAll)
</script>

<style scoped>

.stat-block { margin-bottom: 16px; }
.stat-block h4 {
  margin: 4px 0 8px;
  font-size: 13px;
  color: #555;
  border-left: 3px solid #409eff;
  padding-left: 8px;
}
</style>
