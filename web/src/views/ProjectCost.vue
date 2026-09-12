<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader :title="current ? '项目材料成本台账' : '项目成本'">
        <template #icon><Money /></template>
        <el-button v-if="current" size="small" @click="current = null">
          <el-icon><Back /></el-icon> 返回项目列表
        </el-button>
      </PageHeader>
    </template>

    <!-- ============ 视图一：项目列表 ============ -->
    <div v-if="!current">
      <div class="search-bar">
        <el-input v-model="keyword" placeholder="搜索项目名称/编号/客户" clearable style="width: 260px"
                  @keyup.enter="load(1)" @clear="load(1)">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
        <el-button size="small" @click="resetSearch">重置</el-button>
      </div>
      <el-table :data="items" v-loading="loading" size="small" stripe>
        <el-table-column prop="code" label="编号" width="110" />
        <el-table-column prop="name" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="customer_name" label="客户" min-width="130" show-overflow-tooltip />
        <el-table-column label="材料成本" width="130" align="right" sortable>
          <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
        </el-table-column>
        <el-table-column prop="po_count" label="采购单数" width="90" align="right" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openCost(row)">查看台账</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无项目" :image-size="60" /></template>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes" :total="total"
                     :page-size="size" :current-page="page" :page-sizes="[10, 20, 50, 100]"
                     @current-change="load" @size-change="onSizeChange" />
    </div>

    <!-- ============ 视图二：台账 ============ -->
    <div v-else v-loading="detailLoading">
      <el-descriptions :column="isMobile ? 1 : 2" size="small" border class="mb">
        <el-descriptions-item label="项目名称">{{ detail.project?.name }}</el-descriptions-item>
        <el-descriptions-item label="项目编号">{{ detail.project?.code || '—' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.project?.customer_name }}</el-descriptions-item>
        <el-descriptions-item label="明细条数">{{ detail.items.length }}</el-descriptions-item>
        <el-descriptions-item label="整表数量合计">{{ fmtQty(detail.totals?.[0]?.total_quantity) }}</el-descriptions-item>
        <el-descriptions-item label="整表金额合计">￥{{ fmt(detail.totals?.[0]?.total_amount) }}</el-descriptions-item>
      </el-descriptions>

      <div class="stat-block">
        <h4>供货明细</h4>
        <PaginatedTable :items="detail.items" show-index :page-size="20" empty-text="暂无供货明细">
          <el-table-column prop="po_no" label="采购单号" min-width="130" sortable />
          <el-table-column prop="po_date" label="日期" width="100" sortable>
            <template #default="{ row }">{{ fmtDate(row.po_date) }}</template>
          </el-table-column>
          <el-table-column prop="supplier_name" label="供应商" min-width="130" sortable />
          <el-table-column prop="assembly_system" label="装配系统" min-width="110" />
          <el-table-column prop="spec" label="规格" min-width="110" />
          <el-table-column prop="material_name" label="名称" min-width="140" sortable />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="quantity" label="数量" width="90" align="right" sortable />
          <el-table-column prop="unit_price" label="单价" width="90" align="right" sortable />
          <el-table-column prop="amount" label="金额" width="110" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        </PaginatedTable>
      </div>

      <div class="stat-block">
        <h4>按装配系统汇总</h4>
        <el-table :data="detail.summary" size="small" stripe show-summary :summary-method="summaryMethod">
          <el-table-column prop="assembly_system" label="装配系统" min-width="160" />
          <el-table-column prop="total_quantity" label="数量" width="120" align="right" />
          <el-table-column prop="total_amount" label="金额" width="140" align="right">
            <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
          </el-table-column>
          <template #empty><el-empty description="暂无汇总数据" :image-size="50" /></template>
        </el-table>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Money, Search, Back } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import PaginatedTable from '../components/PaginatedTable.vue'
import { projectApi as api } from '../api/project'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })

const current = ref(null)
const detailLoading = ref(false)
const detail = reactive({ project: {}, items: [], summary: [], totals: {} })

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtQty(v) {
  return Number(v || 0).toLocaleString('zh-CN')
}
/** 后端 date 列按 UTC 序列化(如 2026-08-03T16:00:00.000Z)，需转本地时区取业务日期 */
function fmtDate(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (isNaN(d)) return String(v).slice(0, 10)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function onSizeChange(s) {
  size.value = s
  load(1)
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await api.projects({ keyword: keyword.value, page: page.value, size: size.value })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  keyword.value = ''
  load(1)
}

async function openCost(row) {
  detailLoading.value = true
  try {
    const res = await api.projectCost(row.id)
    Object.assign(detail, res.data)
    current.value = row
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    detailLoading.value = false
  }
}

/** 汇总表合计行：直接取后端整表合计 */
function summaryMethod({ columns, data }) {
  const sums = []
  columns.forEach((col, i) => {
    if (i === 0) {
      sums[i] = '合计'
      return
    }
    if (col.property === 'total_amount') {
      sums[i] = '￥' + fmt(detail.totals?.[0]?.total_amount)
    } else if (col.property === 'total_quantity') {
      sums[i] = fmtQty(detail.totals?.[0]?.total_quantity)
    } else {
      sums[i] = ''
    }
  })
  return sums
}

onMounted(() => load(1))
</script>

<style scoped>
.mb { margin-bottom: 14px; }
.stat-block { margin-bottom: 16px; }
.stat-block h4 {
  margin: 4px 0 8px;
  font-size: 13px;
  color: #555;
  border-left: 3px solid #409eff;
  padding-left: 8px;
}
</style>
