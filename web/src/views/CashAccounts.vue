<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="资金账户">
        <template #icon><Wallet /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新增账户
        </el-button>
      </PageHeader>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
              title="账户余额 = 期初 + 收入 - 支出 + 转入 - 转出；有收支/转账记录的账户不能删除（可改名）" />

    <el-table :data="items" size="small" stripe v-loading="loading" v-if="!isMobile">
      <el-table-column prop="name" label="账户名称" min-width="140" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.account_type === 'cash' ? 'warning' : 'primary'">
            {{ typeName(row.account_type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="initial_balance" label="期初余额" width="130" align="right" sortable>
        <template #default="{ row }">￥{{ fmt(row.initial_balance) }}</template>
      </el-table-column>
      <el-table-column label="当前余额" width="140" align="right" sortable>
        <template #default="{ row }">
          <b :style="{ color: Number(row.balance) < 0 ? '#f56c6c' : '#333' }">￥{{ fmt(row.balance) }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无账户" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>账户名称</span><b>{{ row.name }}</b></div>
          <div class="m-row"><span>期初余额</span><b>{{ '￥' + (row.initial_balance ?? 0) }}</b></div>
          <div class="m-row"><span>备注</span><b>{{ row.remark }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button><el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>

    <el-dialog v-model="visible" :title="editing ? '编辑账户' : '新增账户'" width="460px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item label="账户名称" required>
          <el-input v-model="form.name" placeholder="如：现金、工商银行、微信" size="small" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.accountType" size="small" style="width: 100%">
            <el-option v-for="t in accountTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="期初余额">
          <el-input-number v-model="form.initialBalance" :min="0" :precision="2" size="small" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="visible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Wallet, Plus } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const visible = ref(false)
const editing = ref(false)
const form = reactive({ id: null, name: '', accountType: 'cash', initialBalance: 0, remark: '' })
const accountTypeOptions = ref([])

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function typeName(t) {
  return { cash: '现金', bank: '银行', online: '微信/支付宝' }[t] || t
}

async function load() {
  loading.value = true
  try {
    const r = await request.get('/cash-accounts')
    const bal = await request.get('/account-reports/balances')
    const bmap = Object.fromEntries((bal.data.items || []).map(i => [i.id, i.balance]))
    items.value = (r.data.items || []).map(a => ({ ...a, balance: bmap[a.id] || 0 }))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, name: '', accountType: 'cash', initialBalance: 0, remark: '' })
  visible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, { id: row.id, name: row.name, accountType: row.account_type, initialBalance: Number(row.initial_balance || 0), remark: row.remark || '' })
  visible.value = true
}

async function save() {
  if (!form.name) return alert('请填写账户名称')
  saving.value = true
  try {
    const payload = { name: form.name, type: form.accountType, balance: form.initialBalance, remark: form.remark }
    if (editing.value) {
      const r = await request.put(`/cash-accounts/${form.id}`, payload)
      if (!r.data?.success) return alert(r.data?.error || '保存失败')
    } else {
      const r = await request.post('/cash-accounts', payload)
      if (!r.data?.success) return alert(r.data?.error || '保存失败')
    }
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  if (!confirm(`确认删除账户「${row.name}」？`)) return
  const r = await request.delete(`/cash-accounts/${row.id}`)
  if (!r.data?.success) return alert(r.data?.error || '删除失败')
  load()
}

onMounted(async () => {
  load()
  try {
    const d = await request.get('/dicts')
    accountTypeOptions.value = d.data.dicts.account_types || []
  } catch (e) { /* 字典加载失败 */ }
})
</script>
