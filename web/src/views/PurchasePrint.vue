<template>
  <div class="print-wrap">
    <div class="no-print toolbar">
      <el-button size="small" @click="goBack">← 返回</el-button>
      <el-button type="primary" size="small" @click="print">🖨 打印</el-button>
    </div>

    <div class="sheet">
      <div class="sheet-head">
        <h2>{{ company.companyName }}</h2>
        <p class="title">采 购 单</p>
      </div>

      <div class="meta">
        <span>供应商：{{ main.supplier_name }}</span>
        <span>NO. {{ main.po_no }}</span>
      </div>
      <div class="meta">
        <span>日期：{{ fmtDate(main.po_date) }}</span>
        <span>经手人：{{ main.handler || '' }}</span>
        <span>项目：{{ main.project_name || '' }}</span>
      </div>
      <div v-if="extRows.length" class="meta ext-meta">
        <span v-for="r in extRows" :key="r.name" class="ext-item">{{ r.name }}：{{ r.value }}</span>
      </div>

      <table class="items">
        <thead>
          <tr>
            <th class="c1">物料名称</th>
            <th class="c2">规格</th>
            <th class="c3">单位</th>
            <th class="c4">数量</th>
            <th class="c5">单价</th>
            <th class="c6">金额</th>
            <th class="c7">备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(it, i) in paddedItems" :key="i" :class="{ 'blank-row': !it.id }">
            <td>{{ it.material_name }}</td>
            <td class="spec">{{ it.spec }}</td>
            <td class="center">{{ it.unit }}</td>
            <td class="right">{{ it.id ? it.quantity : '' }}</td>
            <td class="right">{{ it.id ? it.unit_price : '' }}</td>
            <td class="right">{{ it.id ? it.amount : '' }}</td>
            <td>{{ it.remark || '' }}</td>
          </tr>
          <tr class="total-row">
            <td class="total-label" colspan="3">合 计</td>
            <td class="right"><b>{{ main.total_quantity }}</b></td>
            <td></td>
            <td class="right"><b>{{ main.total_amount }}</b></td>
            <td></td>
          </tr>
        </tbody>
      </table>

      <div class="footer">
        <div class="sig-line">
          <span>制单：{{ creatorName }}</span>
          <span>复核：<span class="line"></span></span>
          <span>验收：<span class="line"></span></span>
        </div>
        <div v-if="main.remark" class="note">备注：{{ main.remark }}</div>
        <div v-if="company.phone" class="contact">联系电话：{{ company.phone }}　地址：{{ company.address }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const main = ref({})
const items = ref([])
const fieldDefs = ref([])
const company = ref({ companyName: '', address: '', phone: '' })

const creatorName = computed(() => userStore.displayName || '')

const paddedItems = computed(() => {
  const rows = [...items.value]
  while (rows.length < 5) rows.push({})
  return rows
})

const extMap = computed(() => {
  let ext = {}
  try { ext = JSON.parse(main.value.ext_json || '{}') } catch { /* 脏数据容错 */ }
  return ext
})
const extRows = computed(() =>
  fieldDefs.value
    .map((f) => ({ name: f.fieldName, value: extMap.value[f.fieldKey] ?? '' }))
    .filter((r) => r.value !== '')
)

function fmtDate(v) { return v ? String(v).slice(0, 10) : '' }
function print() { window.print() }
function goBack() { router.back() }

onMounted(async () => {
  const [res, cfg, fields] = await Promise.all([
    request.get(`/purchases/${route.params.id}`),
    request.get('/config/company'),
    request.get('/doc-fields', { params: { docType: 'purchase_order' } }),
  ])
  main.value = res.data.main
  items.value = res.data.items
  if (cfg.data) company.value = cfg.data
  fieldDefs.value = fields.data || []
  document.title = `采购单 ${main.value.po_no}`
})
</script>

<style>
body { margin: 0; background: #f0f2f5; }
.print-wrap { padding: 16px; }
.toolbar { margin-bottom: 12px; }
.sheet {
  background: #fff; width: 248mm; min-height: 100mm; margin: 0 auto 16px;
  padding: 8mm 10mm; box-sizing: border-box; box-shadow: 0 1px 4px rgba(0,0,0,.15);
}
.sheet-head { text-align: center; border-bottom: 2px solid #000; padding-bottom: 3px; }
.sheet-head h2 { margin: 0; font-size: 17px; letter-spacing: 3px; font-weight: bold; }
.sheet-head .title { margin: 2px 0 0; font-size: 24px; font-weight: bold; letter-spacing: 14px; }
.meta {
  display: flex; justify-content: space-between; font-size: 13px;
  margin: 6px 0 5px; font-weight: bold; flex-wrap: wrap; gap: 4px 16px;
}
.meta .customer { letter-spacing: 1px; }
.meta .no { letter-spacing: 1px; }
.ext-meta { font-weight: normal; color: #333; font-size: 12px; }
table.items { width: 100%; border-collapse: collapse; font-size: 12px; }
table.items th, table.items td { border: 1px solid #000; padding: 4px 5px; }
table.items th { font-size: 12px; }
.c1 { width: 22%; } .c2 { width: 16%; } .c3 { width: 8%; }
.c4 { width: 10%; } .c5 { width: 12%; } .c6 { width: 12%; } .c7 { width: 20%; }
.center { text-align: center; } .right { text-align: right; }
.spec { font-size: 11px; }
.blank-row td { height: 24px; }
.total-row td { font-weight: bold; border-top: 2px solid #000; }
.total-label { text-align: left; padding-left: 10px; }
.footer { margin-top: 8px; font-size: 12px; }
.sig-line { display: flex; justify-content: space-between; }
.sig-line span { flex: 1; }
.sig-line .line { display: inline-block; width: 60px; border-bottom: 1px solid #000; }
.note { margin-top: 6px; }
.contact { margin-top: 4px; color: #666; }

@media print {
  body { background: #fff; }
  .print-wrap { padding: 0; }
  .no-print { display: none !important; }
  .sheet { box-shadow: none; margin: 0; page-break-after: always; }
}
</style>
