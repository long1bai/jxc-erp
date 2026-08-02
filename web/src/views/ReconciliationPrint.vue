<template>
  <div class="print-wrap">
    <div class="no-print toolbar">
      <el-button size="small" @click="goBack">← 返回</el-button>
      <el-button type="primary" size="small" @click="window.print()">🖨 打印</el-button>
    </div>

    <div class="sheet">
      <div class="sheet-head">
        <h2>{{ company.companyName }}</h2>
        <p class="sub">{{ isSales ? '销 售 对 账 单' : '采 购 对 账 单' }}</p>
      </div>
      <div class="meta">
        <div class="meta-left">
          <div><b>{{ isSales ? '客户' : '供应商' }}：</b>{{ party.name }}</div>
          <div v-if="party.contact"><b>联系人：</b>{{ party.contact }}</div>
          <div v-if="party.phone"><b>电话：</b>{{ party.phone }}</div>
          <div v-if="party.address"><b>地址：</b>{{ party.address }}</div>
        </div>
        <div class="meta-right">
          <div><b>对账期间：</b>{{ start }} ~ {{ end }}</div>
          <div><b>公司：</b>{{ company.companyName }}</div>
          <div v-if="company.address"><b>地址：</b>{{ company.address }}</div>
          <div v-if="company.phone"><b>电话：</b>{{ company.phone }}{{ company.contactName ? '　联系人：' + company.contactName : '' }}</div>
        </div>
      </div>
      <table class="items">
        <thead>
          <tr>
            <th>序号</th><th>单据编号</th><th>日期</th><th>订单号码</th>
            <th>品名及规格</th><th>单位</th><th>数量</th><th>含税单价</th><th>金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(it, i) in items" :key="i">
            <td class="center">{{ i + 1 }}</td>
            <td>{{ it.dn_no || it.po_no }}</td>
            <td class="center">{{ it.dn_date || it.po_date }}</td>
            <td>{{ it.co_no }}</td>
            <td class="name-spec">{{ it.material_name }}{{ it.spec ? '　' + it.spec : '' }}</td>
            <td class="center">{{ it.unit }}</td>
            <td class="right">{{ it.quantity }}</td>
            <td class="right">{{ it.unit_price }}</td>
            <td class="right">{{ fmt(it.amount) }}</td>
          </tr>
          <tr v-if="!items.length"><td colspan="9" class="center">期间内无业务数据</td></tr>
        </tbody>
      </table>
      <div class="total-row">
        <div class="total-left">
          <b>合计：</b>{{ fmt(total) }} 元（大写：{{ bigTotal }}）
        </div>
        <div class="total-right">共 {{ items.length }} 条</div>
      </div>
      <div class="footer-note">
        本对账单如有异议，请于收到后 5 日内与本公司财务核对，逾期视为确认无误。<br />
        经手人：{{ creatorName }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const party = ref({})
const items = ref([])
const total = ref(0)
const start = ref('')
const end = ref('')
const company = ref({ companyName: '', address: '', phone: '', contactName: '' })
const creatorName = ref('')
const isSales = computed(() => route.query.type !== 'purchase')

const bigTotal = computed(() => amountToChinese(Number(total.value || 0)))

function fmt(v) {
  return Number(v || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function amountToChinese(amount) {
  if (!amount) return '零元整'
  const digits = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖']
  const units = ['', '拾', '佰', '仟', '万', '拾', '佰', '仟', '亿', '拾', '佰', '仟']
  let result = ''
  const [intPart, decPart = ''] = String(amount).split('.')
  const intStr = String(Number(intPart))
  const len = intStr.length
  let zero = false
  for (let i = 0; i < len; i++) {
    const d = Number(intStr[i])
    const pos = len - 1 - i
    if (d === 0) zero = true
    else {
      if (zero && result) result += '零'
      zero = false
      result += digits[d] + units[pos]
    }
  }
  if (result) result += '元'
  else result = '零元'
  const jiao = decPart[0] ? Number(decPart[0]) : 0
  const fen = decPart[1] ? Number(decPart[1]) : 0
  if (jiao === 0 && fen === 0) result += '整'
  else {
    if (jiao > 0) result += digits[jiao] + '角'
    else if (fen > 0) result += '零'
    if (fen > 0) result += digits[fen] + '分'
  }
  return result
}

function goBack() {
  router.back()
}

onMounted(async () => {
  const { type, partyId, start: s, end: e } = route.query
  start.value = s
  end.value = e
  const [res, cfg, me] = await Promise.all([
    request.get('/reports/reconciliation', { params: { type, partyId, start: s, end: e } }),
    request.get('/config/company'),
    request.get('/auth/me'),
  ])
  party.value = res.data.party
  items.value = res.data.items
  total.value = Number(res.data.total)
  if (cfg.data) company.value = cfg.data
  creatorName.value = (me.data && (me.data.displayName || me.data.username)) || ''
  document.title = `${isSales.value ? '销售' : '采购'}对账单`
})
</script>

<style>
body { margin: 0; background: #f0f2f5; }
.print-wrap { padding: 16px; }
.toolbar { margin-bottom: 12px; }
.sheet {
  background: #fff; width: 210mm; min-height: 260mm; margin: 0 auto;
  padding: 12mm 12mm; box-sizing: border-box; box-shadow: 0 1px 4px rgba(0,0,0,.15);
}
.sheet-head { text-align: center; border-bottom: 2px solid #000; padding-bottom: 4px; }
.sheet-head h2 { margin: 0; font-size: 21px; letter-spacing: 2px; }
.sheet-head .sub { margin: 2px 0 0; font-size: 14px; font-weight: bold; }
.meta { display: flex; justify-content: space-between; font-size: 12px; margin: 8px 0; line-height: 1.8; }
table.items { width: 100%; border-collapse: collapse; font-size: 11.5px; }
table.items th, table.items td { border: 1px solid #000; padding: 3px 5px; }
table.items th { background: #f5f5f5; }
.name-spec { font-size: 11px; line-height: 1.5; }
.center { text-align: center; } .right { text-align: right; }
.total-row { display: flex; justify-content: space-between; margin-top: 8px; font-size: 13px; }
.footer-note { margin-top: 14px; font-size: 12px; line-height: 1.9; color: #333; }
@media print {
  body { background: #fff; }
  .print-wrap { padding: 0; }
  .no-print { display: none !important; }
  .sheet { box-shadow: none; margin: 0; }
}
</style>
