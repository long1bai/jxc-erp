<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="报工统计报告">
        <template #icon><TrendCharts /></template>
      </PageHeader>
    </template>

    <el-tabs v-model="tab">
      <!-- 统计报告 -->
      <el-tab-pane label="📊 统计报告" name="stats">
        <div class="search-bar">
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" size="small"
                          range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 250px" />
          <el-select v-model="filterGroup" placeholder="分组" clearable size="small" style="width: 120px">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
          <el-button type="primary" size="small" @click="loadStats">查询</el-button>
        </div>
        <el-table :data="stats" size="small" stripe highlight-current-row @current-change="showTrend" max-height="420" v-if="!isMobile">
          <el-table-column prop="employee_name" label="员工" min-width="100" />
          <el-table-column prop="group_name" label="分组" width="90" />
          <el-table-column prop="process_name" label="工序" min-width="110" />
          <el-table-column prop="total_qty" label="总数量" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total_qty) }}</template>
          </el-table-column>
          <el-table-column prop="report_count" label="报工次数" width="90" align="right"  sortable/>
          <el-table-column prop="work_days" label="天数" width="70" align="right"  sortable/>
          <el-table-column prop="daily_avg" label="日均产量" width="100" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.daily_avg) }}</template>
          </el-table-column>
          <el-table-column label="趋势" width="100">
            <template #default="{ row }">
              <span v-if="row.trend_dir === 'up'" class="trend up">↑ {{ row.trend_change }}%</span>
              <span v-else-if="row.trend_dir === 'down'" class="trend down">↓ {{ Math.abs(row.trend_change) }}%</span>
              <span v-else-if="row.trend_dir === 'flat'" class="trend flat">→</span>
              <span v-else class="trend none">-</span>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无数据" :image-size="60" /></template>
        </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in stats" :key="row.id" class="m-card" @click="showTrend(row)">
        <div class="m-card-head">
          <span class="m-name">{{ row.employee_name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>员工</span><b>{{ row.employee_name }}</b></div>
          <div class="m-row"><span>分组</span><b>{{ row.group_name }}</b></div>
          <div class="m-row"><span>工序</span><b>{{ row.process_name }}</b></div>
          <div class="m-row"><span>总数量</span><b>{{ row.total_qty }}</b></div>
          <div class="m-row"><span>报工次数</span><b>{{ row.report_count }}</b></div>
          <div class="m-row"><span>日均产量</span><b>{{ row.daily_avg }}</b></div>
        </div>
      </div>
      <div v-if="!stats.length" class="m-empty">暂无数据</div>
    </div>
        <div class="hint-box" v-if="trendData.length">
          <div class="sub-title">📈 {{ trendName }} 每日产量趋势（{{ trendData.length }} 天）</div>
          <div class="bars">
            <div v-for="(d, i) in trendData" :key="i" class="bar-col" :title="`${d.report_date}: ${d.daily_qty}`">
              <div class="bar" :style="{ height: barH(d.daily_qty) + 'px' }"></div>
              <div class="bar-label">{{ d.report_date.slice(5) }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 工资汇总 -->
      <el-tab-pane label="💰 工资汇总" name="wages">
        <div class="search-bar">
          <el-date-picker v-model="monthVal" type="month" value-format="YYYY-MM" size="small" style="width: 140px" @change="loadWages" />
          <el-button type="primary" size="small" @click="loadWages">查询</el-button>
          <div class="wage-sum" v-if="wageSummary">
            共 {{ wageSummary.people }} 人 ｜ 总工资 <b>{{ fmt(wageSummary.total_wage) }}</b> ｜ 最高 <b>{{ fmt(wageSummary.max_wage) }}</b>
          </div>
        </div>
        <el-table :data="wageEmps" size="small" stripe max-height="460">
          <el-table-column type="expand">
            <template #default="{ row }">
              <el-table :data="row.details" size="small" border>
                <el-table-column prop="process_name" label="工序" width="140" />
                <el-table-column prop="unit_price" label="单价" width="90" align="right" />
                <el-table-column prop="qty" label="数量" align="right"  sortable/>
                <el-table-column prop="amount" label="金额" align="right" sortable>
                  <template #default="{ row: r }">{{ fmt(r.amount) }}</template>
                </el-table-column>
              </el-table>
            </template>
          </el-table-column>
          <el-table-column prop="employee_name" label="员工" min-width="110" />
          <el-table-column prop="group_name" label="分组" width="100" />
          <el-table-column prop="total_qty" label="总数量" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total_qty) }}</template>
          </el-table-column>
          <el-table-column prop="total_amount" label="工资" align="right" sortable>
            <template #default="{ row }"><b>{{ fmt(row.total_amount) }}</b></template>
          </el-table-column>
          <template #empty><el-empty description="当月无工资数据" :image-size="60" /></template>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const route = useRoute()
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const tab = ref(route.query.tab === 'wages' ? 'wages' : 'stats')
const range = ref([])
const filterGroup = ref(null)
const groups = ref([])
const stats = ref([])
const trendData = ref([])
const trendName = ref('')
const monthVal = ref(today().slice(0, 7))
const wageEmps = ref([])
const wageSummary = ref(null)

function fmt(v) {
  return Number(v || 0).toLocaleString()
}

async function loadStats() {
  const params = {}
  if (range.value && range.value.length === 2) {
    params.from = range.value[0]
    params.to = range.value[1]
  }
  if (filterGroup.value) params.groupId = filterGroup.value
  try {
    const res = await request.get('/work/stats-data', { params })
    stats.value = res.data.items
    trendData.value = []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function showTrend(row) {
  if (!row) return
  trendName.value = `${row.employee_name} - ${row.process_name}`
  try {
    const params = { employeeId: row.employee_id, processName: row.process_name }
    if (range.value && range.value.length === 2) {
      params.from = range.value[0]
      params.to = range.value[1]
    }
    const res = await request.get('/work/trend', { params })
    trendData.value = res.data.items
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function barH(qty) {
  const max = Math.max(...trendData.value.map((d) => Number(d.daily_qty)), 1)
  return Math.max(4, Math.round((Number(qty) / max) * 120))
}

async function loadWages() {
  try {
    const res = await request.get('/work/stats/wages', { params: { month: monthVal.value } })
    wageEmps.value = res.data.employees
    wageSummary.value = res.data.summary
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

onMounted(async () => {
  loadStats()
  loadWages()
  try {
    const g = await request.get('/work/groups')
    groups.value = g.data.items
  } catch { /* 忽略 */ }
})
</script>

<style scoped>
.search-bar { display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; align-items: center; }
.trend { font-size: 13px; }
.trend.up { color: #67c23a; }
.trend.down { color: #f56c6c; }
.trend.flat { color: #909399; }
.trend.none { color: #c0c4cc; }
.sub-title { font-size: 13px; font-weight: 600; margin: 12px 0 6px; color: #303133; }
.bars { display: flex; align-items: flex-end; gap: 3px; height: 140px; overflow-x: auto; padding-top: 6px; }
.bar-col { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; }
.bar { width: 14px; background: #409eff; border-radius: 2px 2px 0 0; min-height: 2px; }
.bar-label { font-size: 10px; color: #909399; transform: rotate(-45deg); margin-top: 2px; white-space: nowrap; }
.wage-sum { font-size: 13px; color: #606266; margin-left: 8px; }

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
