<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="收支报表">
        <template #icon><DataAnalysis /></template>
      </PageHeader>
    </template>

    <FilterBar v-model="range" @query="loadAll" />

    <el-tabs v-model="tab" @tab-change="loadAll" style="margin-top: 4px">
      <!-- 账户余额 -->
      <el-tab-pane label="🏦 账户余额表" name="balances">
        <el-table :data="balances" size="small" stripe max-height="380">
          <el-table-column prop="name" label="账户" min-width="130" sortable />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ typeName(row.account_type) }}</template>
          </el-table-column>
          <el-table-column prop="initial_balance" label="期初" width="120" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.initial_balance) }}</template>
          </el-table-column>
          <el-table-column prop="income" label="收入" width="120" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.income) }}</template>
          </el-table-column>
          <el-table-column prop="expense" label="支出" width="120" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.expense) }}</template>
          </el-table-column>
          <el-table-column prop="transfer_in" label="转入" width="110" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.transfer_in) }}</template>
          </el-table-column>
          <el-table-column prop="transfer_out" label="转出" width="110" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.transfer_out) }}</template>
          </el-table-column>
          <el-table-column label="当前余额" width="130" align="right" sortable>
            <template #default="{ row }">
              <b :style="{ color: Number(row.balance) < 0 ? '#f56c6c' : '#333' }">￥{{ fmt(row.balance) }}</b>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 收支统计 -->
      <el-tab-pane label="📊 收支统计" name="stats">
        <el-table :data="stats" size="small" stripe max-height="420">
          <el-table-column prop="month" label="月份" width="90" sortable />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.ie_type === 'income' ? 'success' : 'danger'">
                {{ row.ie_type === 'income' ? '收入' : '支出' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" min-width="110" sortable />
          <el-table-column prop="amount" label="金额" width="130" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="cnt" label="笔数" width="80" align="right" sortable />
        </el-table>
      </el-tab-pane>

      <!-- 经营状况 -->
      <el-tab-pane label="📈 经营状况月报" name="business">
        <el-table :data="business" size="small" stripe max-height="380">
          <el-table-column prop="month" label="月份" width="100" sortable />
          <el-table-column prop="income" label="收入" width="150" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.income) }}</template>
          </el-table-column>
          <el-table-column prop="expense" label="支出" width="150" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.expense) }}</template>
          </el-table-column>
          <el-table-column label="净收支" width="150" align="right" sortable>
            <template #default="{ row }">
              <b :style="{ color: Number(row.income) - Number(row.expense) >= 0 ? '#67c23a' : '#f56c6c' }">
                ￥{{ fmt(Number(row.income) - Number(row.expense)) }}
              </b>
            </template>
          </el-table-column>
        </el-table>
        <div class="stat-block">
          <h4>说明</h4>
          <p style="font-size: 12px; color: #666; margin: 4px 0">
            经营状况 = 非购销收支（水电/房租/运费/人工/废料收入等）。完整利润 = 销售毛利 − 支出 + 其他收入，见报表中心「利润」与销售报表「毛利分析」。
          </p>
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

const tab = ref('balances')
const range = ref(defaultMonthRange())
const balances = ref([])
const stats = ref([])
const business = ref([])

function defaultMonthRange() {
  const d = new Date()
  const first = new Date(d.getFullYear(), d.getMonth(), 1)
  const fmt = (x) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  return [fmt(first), fmt(d)]
}

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function typeName(t) {
  return { cash: '现金', bank: '银行', online: '微信/支付宝' }[t] || t
}
function params() {
  return { from: range.value?.[0] || '', to: range.value?.[1] || '' }
}

async function loadAll() {
  const p = params()
  const [b, s, m] = await Promise.all([
    request.get('/account-reports/balances'),
    request.get('/account-reports/stats', { params: p }),
    request.get('/account-reports/business', { params: p }),
  ])
  balances.value = b.data.items
  stats.value = s.data.items
  business.value = m.data.items
}

onMounted(loadAll)
</script>

<style scoped>

.stat-block { margin-top: 14px; }
.stat-block h4 {
  margin: 0 0 4px;
  font-size: 13px;
  color: #555;
  border-left: 3px solid #409eff;
  padding-left: 8px;
}
</style>
