<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="收支转账">
        <template #icon><Wallet /></template>
      </PageHeader>
    </template>

    <el-tabs v-model="tab">
      <!-- ===== 收支单 ===== -->
      <el-tab-pane label="💰 收支单" name="ie">
        <div class="toolbar">
          <el-select v-model="ieType" size="small" style="width: 110px" @change="loadIe(1)">
            <el-option label="全部" value="" />
            <el-option label="收入" value="income" />
            <el-option label="支出" value="expense" />
          </el-select>
          <el-button type="primary" size="small" @click="openIe('income')"><el-icon><Plus /></el-icon> 记收入</el-button>
          <el-button type="danger" size="small" @click="openIe('expense')"><el-icon><Plus /></el-icon> 记支出</el-button>
        </div>

        <el-table :data="ieItems" size="small" stripe v-loading="ieLoading" max-height="440">
          <el-table-column prop="ie_no" label="单号" min-width="150" />
          <el-table-column prop="ie_date" label="日期" width="100" sortable />
          <el-table-column label="类型" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.ie_type === 'income' ? 'success' : 'danger'">
                {{ row.ie_type === 'income' ? '收入' : '支出' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" min-width="100" sortable />
          <el-table-column prop="account_id" label="账户" min-width="110">
            <template #default="{ row }">{{ acctName(row.account_id) }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="120" align="right" sortable>
            <template #default="{ row }">
              <b :style="{ color: row.ie_type === 'income' ? '#67c23a' : '#f56c6c' }">
                {{ row.ie_type === 'income' ? '+' : '-' }}￥{{ fmt(row.amount) }}
              </b>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="removeIe(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无收支记录" :image-size="60" /></template>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next" :total="ieTotal"
                       :page-size="ieSize" :current-page="iePage" @current-change="loadIe" />
      </el-tab-pane>

      <!-- ===== 转账 ===== -->
      <el-tab-pane label="🔄 转账" name="tf">
        <div class="toolbar">
          <el-button type="primary" size="small" @click="openTf"><el-icon><Plus /></el-icon> 新建转账</el-button>
        </div>
        <el-table :data="tfItems" size="small" stripe v-loading="tfLoading" max-height="440">
          <el-table-column prop="tf_no" label="转账单号" min-width="150" />
          <el-table-column prop="tf_date" label="日期" width="100" sortable />
          <el-table-column prop="from_name" label="转出账户" min-width="120" />
          <el-table-column prop="to_name" label="转入账户" min-width="120" />
          <el-table-column prop="amount" label="金额" width="120" align="right" sortable>
            <template #default="{ row }">￥{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="removeTf(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无转账记录" :image-size="60" /></template>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next" :total="tfTotal"
                       :page-size="tfSize" :current-page="tfPage" @current-change="loadTf" />
      </el-tab-pane>
    </el-tabs>

    <!-- 收支弹窗 -->
    <el-dialog v-model="ieVisible" :title="ieForm.ieType === 'income' ? '记收入' : '记支出'" width="460px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item label="分类" required>
          <el-select v-model="ieForm.category" filterable allow-create default-first-option size="small"
                     placeholder="选择或输入分类" style="width: 100%">
            <el-option v-for="c in dictList(ieForm.ieType === 'income' ? 'income_categories' : 'expense_categories')" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="账户" required>
          <el-select v-model="ieForm.accountId" size="small" style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input-number v-model="ieForm.amount" :min="0.01" :precision="2" size="small" style="width: 100%" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="ieForm.ieDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 160px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ieForm.remark" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="ieVisible = false">取消</el-button>
        <el-button :type="ieForm.ieType === 'income' ? 'success' : 'danger'" size="small" :loading="saving" @click="saveIe">保存</el-button>
      </template>
    </el-dialog>

    <!-- 转账弹窗 -->
    <el-dialog v-model="tfVisible" title="新建转账" width="460px" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item label="转出账户" required>
          <el-select v-model="tfForm.fromId" size="small" style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="转入账户" required>
          <el-select v-model="tfForm.toId" size="small" style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input-number v-model="tfForm.amount" :min="0.01" :precision="2" size="small" style="width: 100%" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="tfForm.tfDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 160px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="tfForm.remark" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="tfVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="saveTf">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Wallet, Plus } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

const tab = ref('ie')
const accounts = ref([])
const acctMap = ref({})

// 收支
const ieItems = ref([])
const ieTotal = ref(0)
const iePage = ref(1)
const ieSize = ref(20)
const ieLoading = ref(false)
const ieType = ref('')
const ieVisible = ref(false)
const saving = ref(false)
const ieForm = reactive({ ieType: 'income', category: '', accountId: null, amount: 0, ieDate: '', remark: '' })

// 转账
const tfItems = ref([])
const tfTotal = ref(0)
const tfPage = ref(1)
const tfSize = ref(20)
const tfLoading = ref(false)
const tfVisible = ref(false)
const tfForm = reactive({ fromId: null, toId: null, amount: 0, tfDate: '', remark: '' })

const categories = ref([])
const dicts = ref({})

function dictList(key) {
  return (dicts.value[key] || []).map(d => d.label)
}

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function acctName(id) {
  return acctMap.value[id] || '-'
}

async function loadIe(p) {
  if (p) iePage.value = p
  ieLoading.value = true
  try {
    const r = await request.get('/income-expenses', {
      params: { keyword: '', type: ieType.value, page: iePage.value, size: ieSize.value },
    })
    ieItems.value = r.data.items
    ieTotal.value = r.data.total
  } finally {
    ieLoading.value = false
  }
}

async function loadTf(p) {
  if (p) tfPage.value = p
  tfLoading.value = true
  try {
    const r = await request.get('/transfers', { params: { page: tfPage.value, size: tfSize.value } })
    tfItems.value = r.data.items
    tfTotal.value = r.data.total
  } finally {
    tfLoading.value = false
  }
}

function openIe(type) {
  Object.assign(ieForm, { ieType: type, category: '', accountId: null, amount: 0, ieDate: new Date().toISOString().slice(0, 10), remark: '' })
  ieVisible.value = true
}

async function saveIe() {
  if (!ieForm.category) return alert('请选择分类')
  if (!ieForm.accountId) return alert('请选择账户')
  if (!ieForm.amount || ieForm.amount <= 0) return alert('请填写金额')
  saving.value = true
  try {
    const r = await request.post('/income-expenses', { ...ieForm })
    if (!r.data?.success) return alert(r.data?.error || '保存失败')
    ieVisible.value = false
    loadIe(1)
  } finally {
    saving.value = false
  }
}

async function removeIe(row) {
  if (!confirm(`确认删除收支单 ${row.ie_no}？`)) return
  await request.delete(`/income-expenses/${row.id}`)
  loadIe(iePage.value)
}

function openTf() {
  Object.assign(tfForm, { fromId: null, toId: null, amount: 0, tfDate: new Date().toISOString().slice(0, 10), remark: '' })
  tfVisible.value = true
}

async function saveTf() {
  if (!tfForm.fromId || !tfForm.toId || tfForm.fromId === tfForm.toId) return alert('请选择两个不同账户')
  if (!tfForm.amount || tfForm.amount <= 0) return alert('请填写金额')
  saving.value = true
  try {
    const r = await request.post('/transfers', { ...tfForm })
    if (!r.data?.success) return alert(r.data?.error || '保存失败')
    tfVisible.value = false
    loadTf(1)
  } finally {
    saving.value = false
  }
}

async function removeTf(row) {
  if (!confirm(`确认删除转账单 ${row.tf_no}？`)) return
  await request.delete(`/transfers/${row.id}`)
  loadTf(tfPage.value)
}

onMounted(async () => {
  loadIe(1)
  loadTf(1)
  const [r, d] = await Promise.all([
    request.get('/cash-accounts'),
    request.get('/dicts'),
  ])
  accounts.value = r.data.items
  acctMap.value = Object.fromEntries(r.data.items.map(a => [a.id, a.name]))
  dicts.value = d.data.dicts
})
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 10px; }
.pager { margin-top: 10px; justify-content: flex-end; }
</style>
