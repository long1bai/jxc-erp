<template>
  <div class="print-wrap">
    <div class="no-print toolbar">
      <el-button size="small" @click="goBack">← 返回</el-button>
      <el-button type="primary" size="small" @click="print">🖨 打印</el-button>
    </div>

    <div class="sheet">
      <div class="sheet-head">
        <h2>{{ company.companyName }}</h2>
        <p class="title">送 货 单</p>
      </div>

      <div class="meta">
        <span class="customer">客户名称：{{ main.customer_name }}</span>
        <span class="no">NO. {{ main.dn_no }}</span>
      </div>

      <table class="items">
        <thead>
          <tr>
            <th class="c1">订单号码</th>
            <th class="c2">物料编码</th>
            <th class="c3">商品名称</th>
            <th class="c4">规格</th>
            <th class="c5">单位</th>
            <th class="c6">数量</th>
            <th class="c7">备 注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(it, i) in paddedItems" :key="i" :class="{ 'blank-row': !it.id }">
            <td class="center">{{ it.order_no }}</td>
            <td>{{ it.material_code }}</td>
            <td>{{ it.material_name }}</td>
            <td class="spec">{{ it.spec }}</td>
            <td class="center">{{ it.unit }}</td>
            <td class="right">{{ it.id ? it.quantity : '' }}</td>
            <td>{{ it.remark }}</td>
          </tr>
          <tr class="total-row">
            <td class="total-label" colspan="4">合 计</td>
            <td class="center">{{ main.unit || (items[0] && items[0].unit) || '' }}</td>
            <td class="right"><b>{{ main.total_quantity }}</b></td>
            <td></td>
          </tr>
        </tbody>
      </table>

      <div class="footer">
        <div class="sig-line">
          <span>制单：{{ creatorName }}</span>
          <span>收货人签章：<span class="line"></span></span>
          <span>送货人签章：<span class="line"></span></span>
        </div>
        <div class="note">备注：收货后请及时检查验收，如有问题7天内请与我们联系调货。</div>
        <div v-if="company.phone" class="contact">联系电话：{{ company.phone }}　地址：{{ company.address }}</div>
      </div>

      <!-- 联次竖排标注 -->
      <div class="copies">
        <span class="copy white">白联存根</span>
        <span class="copy red">红联客户</span>
        <span class="copy blue">蓝联回单</span>
        <span class="copy yellow">黄财务</span>
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
const company = ref({ companyName: '', address: '', phone: '' })

const creatorName = computed(() => userStore.displayName || '')

const paddedItems = computed(() => {
  const rows = [...items.value]
  while (rows.length < 5) rows.push({})
  return rows
})

function print() {
  window.print()
}
function goBack() {
  router.back()
}

onMounted(async () => {
  const [res, cfg] = await Promise.all([
    request.get(`/deliveries/${route.params.id}`),
    request.get('/config/company'),
  ])
  main.value = res.data.main
  items.value = res.data.items
  if (cfg.data) {
    company.value = cfg.data
  }
  document.title = `送货单 ${main.value.dn_no}`
})
</script>

<style>
body { margin: 0; background: #f0f2f5; }
.print-wrap { padding: 16px; }
.toolbar { margin-bottom: 12px; }
.sheet {
  background: #fff; width: 248mm; min-height: 100mm; margin: 0 auto 16px;
  padding: 8mm 10mm; box-sizing: border-box; box-shadow: 0 1px 4px rgba(0,0,0,.15);
  position: relative;
}
.sheet-head { text-align: center; border-bottom: 2px solid #000; padding-bottom: 3px; }
.sheet-head h2 { margin: 0; font-size: 17px; letter-spacing: 3px; font-weight: bold; }
.sheet-head .title { margin: 2px 0 0; font-size: 24px; font-weight: bold; letter-spacing: 14px; }
.meta {
  display: flex; justify-content: space-between; font-size: 13px;
  margin: 6px 0 5px; font-weight: bold;
}
.meta .customer { letter-spacing: 1px; }
.meta .no { letter-spacing: 1px; }
table.items { width: 100%; border-collapse: collapse; font-size: 12px; }
table.items th, table.items td { border: 1px solid #000; padding: 4px 5px; }
table.items th { font-size: 12px; }
.c1 { width: 10%; } .c2 { width: 10%; } .c3 { width: 20%; } .c4 { width: 20%; }
.c5 { width: 8%; } .c6 { width: 10%; } .c7 { width: 22%; }
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
.copies {
  position: absolute; right: 2mm; top: 14mm; display: flex; flex-direction: column;
  font-size: 10px; line-height: 1.9; text-align: right; color: #333;
}
.copy { border: 1px solid #999; padding: 0 3px; margin-bottom: 2px; }
.copy.white { color: #333; }
.copy.red { color: #c00; }
.copy.blue { color: #00c; }
.copy.yellow { color: #b8860b; }

@media print {
  body { background: #fff; }
  .print-wrap { padding: 0; }
  .no-print { display: none !important; }
  .sheet { box-shadow: none; margin: 0; page-break-after: always; }
}
</style>
