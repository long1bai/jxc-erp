<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="应付款管理">
        <template #icon><ArrowUp /></template>
      </PageHeader>
    </template>

    <el-tabs v-model="tab">
      <!-- ============ 汇总 ============ -->
      <el-tab-pane label="📊 汇总" name="summary">
        <el-table :data="summary" size="small" stripe max-height="520">
          <el-table-column prop="party_name" label="供应商" min-width="180" show-overflow-tooltip />
          <el-table-column prop="total_amount" label="应付总额" width="130" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total_amount) }}</template>
          </el-table-column>
          <el-table-column prop="settled_amount" label="已付款" width="130" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.settled_amount) }}</template>
          </el-table-column>
          <el-table-column prop="balance" label="未付款" width="130" align="right" sortable>
            <template #default="{ row }"><b :class="row.balance > 0 ? 'text-danger' : ''">{{ fmt(row.balance) }}</b></template>
          </el-table-column>
          <template #empty><el-empty description="暂无数据" :image-size="60" /></template>
        </el-table>
        <div class="sum-bar">合计：应付 <b>{{ fmt(sumOf('total_amount')) }}</b> ｜ 已付 <b>{{ fmt(sumOf('settled_amount')) }}</b> ｜ 未付 <b class="text-danger">{{ fmt(sumOf('balance')) }}</b></div>
      </el-tab-pane>

      <!-- ============ 单据（金蝶式） ============ -->
      <el-tab-pane label="🧾 单据" name="docs">
        <div class="search-bar">
          <el-input v-model="filter.kw" placeholder="搜索单号/供应商" clearable size="small" style="width: 200px"
                    @keyup.enter="loadDocs(1)" @clear="loadDocs(1)" />
          <el-select v-model="filter.status" placeholder="结清状态" clearable size="small" style="width: 130px" @change="loadDocs(1)">
            <el-option label="未结清" value="none" />
            <el-option label="部分结清" value="partial" />
            <el-option label="已结清" value="settled" />
          </el-select>
          <el-date-picker v-model="filter.range" type="daterange" value-format="YYYY-MM-DD" size="small"
                          range-separator="~" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px"
                          @change="loadDocs(1)" />
          <el-button type="primary" size="small" @click="loadDocs(1)">查询</el-button>
          <el-button size="small" @click="resetFilter">清空</el-button>
          <el-button type="warning" size="small" :disabled="!selectedDoc" @click="openSettle">
            <el-icon><Link /></el-icon> 付款核销
          </el-button>
        </div>

        <el-table :data="docs" size="small" stripe highlight-current-row @current-change="selectDoc"
                  v-loading="docLoading" max-height="420">
          <el-table-column prop="doc_no" label="采购单号" min-width="175" />
          <el-table-column prop="doc_date" label="日期" width="100" />
          <el-table-column prop="party_name" label="供应商" min-width="150" show-overflow-tooltip />
          <el-table-column prop="amount" label="应付款" width="110" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="settled_amount" label="已付款" width="100" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.settled_amount) }}</template>
          </el-table-column>
          <el-table-column prop="balance" label="余额" width="110" align="right" sortable>
            <template #default="{ row }"><b :class="row.balance > 0 ? 'text-danger' : ''">{{ fmt(row.balance) }}</b></template>
          </el-table-column>
          <el-table-column label="结清状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.settle_status === '已结清' ? 'success' : row.settle_status === '部分结清' ? 'warning' : 'danger'" size="small">
                {{ row.settle_status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开票状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.uninvoiced_amount <= 0 ? 'success' : row.invoiced_amount > 0 ? 'warning' : 'danger'" size="small">
                {{ row.uninvoiced_amount <= 0 ? '已收票' : row.invoiced_amount > 0 ? '部分收票' : '未收票' }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无单据" :image-size="60" /></template>
        </el-table>

        <!-- 底部结款详情 -->
        <div class="settle-box" v-if="selectedDoc">
          <div class="settle-title">结款详情 — {{ selectedDoc.doc_no }}（余额 {{ fmt(selectedDoc.balance) }}）</div>
          <el-table :data="settlements" size="small" max-height="160">
            <el-table-column prop="voucher_no" label="付款单号" min-width="160" />
            <el-table-column prop="amount" label="核销金额" width="110" align="right" sortable>
              <template #default="{ row }">{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="revoke(row)">撤销</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="该单据暂无核销记录" :image-size="40" /></template>
          </el-table>
        </div>

        <el-pagination class="pager" background layout="total, prev, pager, next" :total="docTotal"
                       :page-size="docSize" :current-page="docPage" @current-change="loadDocs" />
      </el-tab-pane>

      <!-- ============ 分月 ============ -->
      <el-tab-pane label="📅 分月汇总" name="monthly">
        <el-table :data="monthly" size="small" stripe max-height="520">
          <el-table-column prop="month" label="月份" width="120"  sortable/>
          <el-table-column prop="purchases" label="进货金额" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.purchases) }}</template>
          </el-table-column>
          <el-table-column prop="paid" label="当月付款" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.paid) }}</template>
          </el-table-column>
          <el-table-column label="净额" align="right">
            <template #default="{ row }"><b :class="row.purchases - row.paid > 0 ? 'text-danger' : ''">{{ fmt(row.purchases - row.paid) }}</b></template>
          </el-table-column>
          <template #empty><el-empty description="暂无数据" :image-size="60" /></template>
        </el-table>
      </el-tab-pane>

      <!-- ============ 账龄 ============ -->
      <el-tab-pane label="⏳ 账龄分析" name="aging">
        <el-table :data="aging" size="small" stripe max-height="520">
          <el-table-column prop="party_name" label="供应商" min-width="170" show-overflow-tooltip />
          <el-table-column prop="age_30" label="1-30天" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.age_30) }}</template>
          </el-table-column>
          <el-table-column prop="age_60" label="31-60天" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.age_60) }}</template>
          </el-table-column>
          <el-table-column prop="age_90" label="61-90天" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.age_90) }}</template>
          </el-table-column>
          <el-table-column prop="age_120" label="90天以上" align="right" sortable>
            <template #default="{ row }"><b class="text-danger">{{ fmt(row.age_120) }}</b></template>
          </el-table-column>
          <el-table-column prop="total_balance" label="未付合计" width="120" align="right">
            <template #default="{ row }"><b>{{ fmt(row.total_balance) }}</b></template>
          </el-table-column>
          <template #empty><el-empty description="暂无未结清应付" :image-size="60" /></template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 付款核销弹窗 -->
    <el-dialog v-model="settleVisible" title="付款核销" width="560px">
      <el-alert type="info" :closable="false" class="mb">
        单据「{{ selectedDoc?.doc_no }}」未付余额 <b>{{ fmt(selectedDoc?.balance) }}</b>，选择该供应商的付款单进行核销。
      </el-alert>
      <el-form label-width="90px" size="small">
        <el-form-item label="付款单" required>
          <el-select v-model="settleForm.voucherId" filterable placeholder="选择付款单" style="width: 100%">
            <el-option v-for="v in candidateVouchers" :key="v.id" :value="v.id"
                       :label="`${v.pv_no}（${fmt(v.amount)}，剩余 ${fmt(v.amount - (v.settled_amount || 0))}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="核销金额" required>
          <el-input-number v-model="settleForm.amount" :min="0.01" :max="Number(selectedDoc?.balance || 0)"
                           :precision="2" :step="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="settleForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="settleVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="settling" @click="doSettle">确认核销</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { financeApi as api } from '../api/finance'

const tab = ref('summary')
const summary = ref([])
const monthly = ref([])
const docs = ref([])
const docTotal = ref(0)
const docPage = ref(1)
const docSize = ref(20)
const docLoading = ref(false)
const selectedDoc = ref(null)
const settlements = ref([])
const filter = reactive({ kw: '', status: '', range: null })
const settleVisible = ref(false)
const settleForm = reactive({ voucherId: null, amount: 0, remark: '' })
const candidateVouchers = ref([])
const settling = ref(false)
const aging = ref([])

function fmt(v) {
  return Number(v || 0).toLocaleString()
}
function sumOf(key) {
  return summary.value.reduce((s, r) => s + Number(r[key] || 0), 0)
}

async function loadSummary() {
  const res = await api.payableSummary()
  summary.value = res.data.items
}
async function loadMonthly() {
  const res = await api.payableMonthly()
  monthly.value = res.data.items
}
async function loadDocs(p) {
  if (p) docPage.value = p
  docLoading.value = true
  try {
    const params = { type: 'purchase', page: docPage.value, size: docSize.value, keyword: filter.kw }
    if (filter.status) params.status = filter.status
    if (filter.range && filter.range.length === 2) {
      params.start = filter.range[0]
      params.end = filter.range[1]
    }
    const res = await api.docList(params)
    docs.value = res.data.items
    docTotal.value = Number(res.data.total)
  } finally {
    docLoading.value = false
  }
}
function resetFilter() {
  filter.kw = ''
  filter.status = ''
  filter.range = null
  loadDocs(1)
}

async function selectDoc(row) {
  selectedDoc.value = row
  if (!row) {
    settlements.value = []
    return
  }
  const res = await api.settlements({ refType: 'purchase', refId: row.id })
  settlements.value = res.data.items
}

async function openSettle() {
  settleForm.voucherId = null
  settleForm.amount = Number(selectedDoc.value.balance) || 0
  settleForm.remark = ''
  try {
    const res = await api.vouchers('payments', {
      params: { keyword: selectedDoc.value.party_name, page: 1, size: 100 },
    })
    candidateVouchers.value = (res.data.items || []).filter(
      (v) => Number(v.amount) - Number(v.settled_amount || 0) > 0
    )
  } catch { candidateVouchers.value = [] }
  settleVisible.value = true
}

async function doSettle() {
  if (!settleForm.voucherId || !settleForm.amount) {
    ElMessage.warning('请选择付款单并填写金额')
    return
  }
  settling.value = true
  try {
    await api.settle({
      refType: 'purchase', refId: selectedDoc.value.id,
      voucherId: settleForm.voucherId, amount: settleForm.amount, remark: settleForm.remark,
    })
    ElMessage.success('核销成功')
    settleVisible.value = false
    await Promise.all([loadDocs(), selectDoc(selectedDoc.value), loadSummary()])
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    settling.value = false
  }
}

async function revoke(row) {
  try {
    await ElMessageBox.confirm(`确定撤销核销 ${fmt(row.amount)} 元？`, '撤销确认', { type: 'warning' })
  } catch { return }
  try {
    await api.revokeSettle({ id: row.id })
    ElMessage.success('已撤销')
    await Promise.all([loadDocs(), selectDoc(selectedDoc.value), loadSummary()])
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadAging() {
  const res = await api.aging({ type: 'purchase' })
  aging.value = res.data.items
}

onMounted(() => { loadSummary(); loadMonthly(); loadDocs(1); loadAging() })
</script>

<style scoped>
.search-bar { display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; align-items: center; }
.pager { margin-top: 10px; justify-content: flex-end; }
.sum-bar { margin-top: 10px; font-size: 13px; color: #606266; text-align: right; }
.text-danger { color: #f56c6c; }
.settle-box { margin-top: 10px; border: 1px solid #e4e7ed; border-radius: 4px; padding: 8px; }
.settle-title { font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #303133; }
.mb { margin-bottom: 10px; }
</style>
