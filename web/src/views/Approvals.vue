<template>
  <div>
    <PageHeader title="单据审批">
      <template #icon><Stamp /></template>
    </PageHeader>
    <el-alert type="info" :closable="false" class="mb">
      审批启用后，新采购/送货单需管理员审批通过才生效（入库加库存/出货扣库存在审批时执行）。当前审批开关：采购 {{ cfg.purchase ? '开' : '关' }} / 送货 {{ cfg.delivery ? '开' : '关' }}
      <el-switch v-model="cfg.purchase" size="small" style="margin-left: 8px" @change="saveCfg" /> 采购审批
      <el-switch v-model="cfg.delivery" size="small" style="margin-left: 12px" @change="saveCfg" /> 送货审批
    </el-alert>

    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="📥 待审批采购单" name="purchase">
        <el-table :data="purchases" size="small" stripe v-loading="loading">
          <el-table-column prop="po_no" label="单号" min-width="150" />
          <el-table-column prop="supplier_name" label="供应商" min-width="150" />
          <el-table-column prop="po_date" label="日期" width="100" />
          <el-table-column prop="handler" label="经手人" width="100" />
          <el-table-column prop="total_quantity" label="数量" width="100" align="right" />
          <el-table-column prop="total_amount" label="金额" width="120" align="right">
            <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button type="success" size="small" @click="doApprove('purchase', row.id)">通过</el-button>
              <el-button type="danger" size="small" @click="doReject('purchase', row.id)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !purchases.length" description="暂无待审批采购单" />
      </el-tab-pane>
      <el-tab-pane label="📤 待审批送货单" name="delivery">
        <el-table :data="deliveries" size="small" stripe v-loading="loading">
          <el-table-column prop="dn_no" label="单号" min-width="150" />
          <el-table-column prop="customer_name" label="客户" min-width="150" />
          <el-table-column prop="dn_date" label="日期" width="100" />
          <el-table-column prop="handler" label="经手人" width="100" />
          <el-table-column prop="total_quantity" label="数量" width="100" align="right" />
          <el-table-column prop="total_amount" label="金额" width="120" align="right">
            <template #default="{ row }">￥{{ fmt(row.total_amount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button type="success" size="small" @click="doApprove('delivery', row.id)">通过</el-button>
              <el-button type="danger" size="small" @click="doReject('delivery', row.id)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !deliveries.length" description="暂无待审批送货单" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Stamp } from '@element-plus/icons-vue'
import PageHeader from '../components/PageHeader.vue'
import request from '../utils/request'

const tab = ref('purchase')
const loading = ref(false)
const purchases = ref([])
const deliveries = ref([])
const cfg = reactive({ purchase: false, delivery: false })

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const [p, d] = await Promise.all([
      request.get('/approvals/pending'),
      request.get('/approvals/config'),
    ])
    purchases.value = p.data.purchases || []
    deliveries.value = p.data.deliveries || []
    cfg.purchase = !!d.data.purchase
    cfg.delivery = !!d.data.delivery
  } finally {
    loading.value = false
  }
}

async function doApprove(type, id) {
  await ElMessageBox.confirm('审批通过后将执行库存动作（采购加库存/送货扣库存），确认？', '审批', { type: 'warning' })
  await request.post(`/approvals/${type}/${id}/approve`)
  ElMessage.success('已通过')
  load()
}

async function doReject(type, id) {
  await ElMessageBox.confirm('驳回后单据标记 rejected，不会执行库存动作。确认？', '驳回', { type: 'warning' })
  await request.post(`/approvals/${type}/${id}/reject`)
  ElMessage.success('已驳回')
  load()
}

async function saveCfg() {
  await request.post('/approvals/config', { purchase: cfg.purchase, delivery: cfg.delivery })
  ElMessage.success('审批开关已保存')
}

onMounted(load)
</script>
