<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="收付款单">
        <template #icon><Money /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建{{ tab === 'receipts' ? '收款单' : '付款单' }}
        </el-button>
      </PageHeader>
    </template>

    <el-tabs v-model="tab" @tab-change="load(1)">
      <!-- 收款单 -->
      <el-tab-pane label="💰 收款单" name="receipts">
        <div class="search-bar">
          <el-input v-model="keyword" placeholder="搜索单号/客户" clearable size="small" style="width: 220px"
                    @keyup.enter="load(1)" @clear="load(1)" />
          <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
        </div>
        <el-table :data="items" size="small" stripe v-loading="loading" max-height="480">
          <el-table-column prop="rv_no" label="收款单号" min-width="170" />
          <el-table-column prop="receipt_date" label="日期" width="100" />
          <el-table-column prop="customer_name" label="客户" min-width="150" show-overflow-tooltip />
          <el-table-column prop="amount" label="金额" width="120" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="settled_amount" label="已核销" width="100" align="right">
            <template #default="{ row }">{{ fmt(row.settled_amount) }}</template>
          </el-table-column>
          <el-table-column prop="receipt_method" label="收款方式" width="90" />
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无收款单" :image-size="60" /></template>
        </el-table>
      </el-tab-pane>

      <!-- 付款单 -->
      <el-tab-pane label="💸 付款单" name="payments">
        <div class="search-bar">
          <el-input v-model="keyword" placeholder="搜索单号/供应商" clearable size="small" style="width: 220px"
                    @keyup.enter="load(1)" @clear="load(1)" />
          <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
        </div>
        <el-table :data="items" size="small" stripe v-loading="loading" max-height="480">
          <el-table-column prop="pv_no" label="付款单号" min-width="170" />
          <el-table-column prop="pay_date" label="日期" width="100" />
          <el-table-column prop="supplier_name" label="供应商" min-width="150" show-overflow-tooltip />
          <el-table-column prop="amount" label="金额" width="120" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="settled_amount" label="已核销" width="100" align="right">
            <template #default="{ row }">{{ fmt(row.settled_amount) }}</template>
          </el-table-column>
          <el-table-column prop="pay_method" label="付款方式" width="90" />
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无付款单" :image-size="60" /></template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="load" />

    <!-- 新建弹窗 -->
    <el-dialog v-model="createVisible" :title="'新建' + (tab === 'receipts' ? '收款单' : '付款单')" width="480px">
      <el-form ref="formRef" :rules="formRules" label-width="90px" size="small">
        <el-form-item :label="tab === 'receipts' ? '客户' : '供应商'" required prop="partyId">
          <el-select v-model="form.partyId" filterable placeholder="选择" style="width: 100%">
            <el-option v-for="p in parties" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" required prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item :label="tab === 'receipts' ? '收款方式' : '付款方式'">
          <el-select v-model="form.method" style="width: 200px">
            <el-option label="转账" value="转账" />
            <el-option label="现金" value="现金" />
            <el-option label="支票" value="支票" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { financeApi as api } from '../api/finance'
import request from '../utils/request'

const route = useRoute()
const tab = ref('receipts')
// 支持菜单带 ?tab= 预选（收款单/付款单直达）
const routeTab = new URLSearchParams(window.location.hash.split('?')[1] || '').get('tab')
if (routeTab === 'receipts' || routeTab === 'payments') {
  tab.value = routeTab
}
// 同一组件内切换 query 不会重挂载，需监听
watch(() => route.query.tab, (v) => {
  if (v === 'receipts' || v === 'payments') tab.value = v
})
const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const formRef = ref(null)
const formRules = {
  partyId: [{ required: true, message: '请选择往来单位', trigger: 'change' }],
  amount: [{ required: true, message: '请填写金额', trigger: 'change' }],
}
const saving = ref(false)
const createVisible = ref(false)
const parties = ref([])
const form = reactive({ partyId: null, amount: 100, date: today(), method: '转账', remark: '' })

const isReceipt = computed(() => tab.value === 'receipts')

function fmt(v) {
  return Number(v || 0).toLocaleString()
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const type = isReceipt.value ? 'receipts' : 'payments'
    const res = await api.vouchers(type, { keyword: keyword.value, page: page.value, size: size.value })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  Object.assign(form, { partyId: null, amount: 100, date: today(), method: '转账', remark: '' })
  try {
    // 收款单往来单位=客户，付款单=供应商
    const res = isReceipt.value
      ? await request.get('/customers', { params: { page: 1, size: 100 } })
      : await request.get('/suppliers', { params: { page: 1, size: 100 } })
    parties.value = res.data.items
  } catch { parties.value = [] }
  createVisible.value = true
}

async function save() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!form.partyId || !form.amount) {
    ElMessage.warning('请选择往来单位并填写金额')
    return
  }
  saving.value = true
  try {
    if (isReceipt.value) {
      await api.createVoucher('receipts', {
        customerId: form.partyId, amount: form.amount, receiptDate: form.date,
        receiptMethod: form.method, remark: form.remark,
      })
    } else {
      await api.createVoucher('payments', {
        supplierId: form.partyId, amount: form.amount, payDate: form.date,
        payMethod: form.method, remark: form.remark,
      })
    }
    ElMessage.success('保存成功')
    createVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  const label = isReceipt.value ? '收款单' : '付款单'
  try {
    await ElMessageBox.confirm(`确定删除${label}「${row[isReceipt.value ? 'rv_no' : 'pv_no']}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    const type = isReceipt.value ? 'receipts' : 'payments'
    await api.deleteVoucher(type, row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(tab, () => { keyword.value = '' })

function today() {
  return new Date().toISOString().slice(0, 10)
}

onMounted(() => load(1))
</script>

<style scoped>


</style>
