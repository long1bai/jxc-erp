<template>
  <div class="dash-wrap">
    <!-- 统计卡片：2行×3列 -->
    <el-row :gutter="8" class="mb-2">
      <el-col :span="8">
        <div class="dash-stat" style="background:linear-gradient(135deg,#1890ff,#096dd9)">
          <div class="lbl"><el-icon><ShoppingCart /></el-icon> 待处理订单</div>
          <div class="num">{{ data.stats.pending_orders || 0 }}</div>
          <div class="sub">未出 {{ data.stats.full_unshipped || 0 }}/部分 {{ data.stats.partial_orders || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="dash-stat" style="background:linear-gradient(135deg,#52c41a,#389e0d)">
          <div class="lbl"><el-icon><Van /></el-icon> 本月销售</div>
          <div class="num">¥{{ fmt(data.stats.month_sales) }}</div>
          <div class="sub">{{ data.stats.month_deliveries || 0 }} 次送货</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="dash-stat" style="background:linear-gradient(135deg,#fa8c16,#d46b08)">
          <div class="lbl"><el-icon><Box /></el-icon> 本月进货</div>
          <div class="num">¥{{ fmt(data.stats.month_purchases) }}</div>
          <div class="sub">{{ data.stats.month_new_orders || 0 }} 新订单</div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="8" class="mb-2">
      <el-col :span="8">
        <div class="dash-stat" style="background:linear-gradient(135deg,#ff4d4f,#cf1322)">
          <div class="lbl"><el-icon><Warning /></el-icon> 库存预警</div>
          <div class="num">{{ data.stats.stock_alerts || 0 }}</div>
          <div class="sub">低于安全库存</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="dash-stat" style="background:linear-gradient(135deg,#722ed1,#531dab)">
          <div class="lbl"><el-icon><Top /></el-icon> 本月付款</div>
          <div class="num">¥{{ fmt(data.stats.month_payments) }}</div>
          <div class="sub">付给供应商</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="dash-stat" style="background:linear-gradient(135deg,#eb2f96,#c41d7f)">
          <div class="lbl"><el-icon><Bottom /></el-icon> 本月收款</div>
          <div class="num">¥{{ fmt(data.stats.month_receipts) }}</div>
          <div class="sub">来自客户</div>
        </div>
      </el-col>
    </el-row>

    <!-- 应收应付 -->
    <el-card shadow="never" class="mb-2">
      <template #header>
        <div class="card-head">
          <span class="head-title"><el-icon><Coin /></el-icon> 应收应付</span>
          <router-link to="/finance/receivables" class="head-link">详情 →</router-link>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="12">
          <div class="col-title">🔴 应付款</div>
          <div v-for="p in data.payables" :key="p.party_id" class="row-line">
            <span class="line-name">{{ p.party_name }}</span>
            <span class="line-amt" :class="Number(p.balance) > 0 ? 'text-danger' : 'text-success'">¥{{ fmt(p.balance) }}</span>
          </div>
          <div v-if="!data.payables.length" class="text-muted">暂无</div>
        </el-col>
        <el-col :span="12">
          <div class="col-title">🟢 应收款</div>
          <div v-for="r in data.receivables" :key="r.party_id" class="row-line">
            <span class="line-name">{{ r.party_name }}</span>
            <span class="line-amt" :class="Number(r.balance) > 0 ? 'text-danger' : 'text-success'">¥{{ fmt(r.balance) }}</span>
          </div>
          <div v-if="!data.receivables.length" class="text-muted">暂无</div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 待出货 + 部分出货 -->
    <el-row :gutter="8" class="mb-2">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="head-title"><el-icon><Clock /></el-icon> 待出货订单</span>
              <router-link to="/orders" class="head-link">查看全部</router-link>
            </div>
          </template>
          <el-table :data="data.pending_list" size="small" max-height="220">
            <el-table-column prop="co_no" label="单号" min-width="150" />
            <el-table-column prop="customer_name" label="客户" min-width="100" show-overflow-tooltip />
            <el-table-column prop="total_amount" label="金额" width="90" align="right">
              <template #default="{ row }">¥{{ fmt(row.total_amount) }}</template>
            </el-table-column>
            <template #empty><el-empty description="暂无待出货订单 ✅" :image-size="40" /></template>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="head-title"><el-icon><Van /></el-icon> 部分出货订单</span>
              <router-link to="/orders" class="head-link">查看全部</router-link>
            </div>
          </template>
          <el-table :data="data.partial_list" size="small" max-height="220">
            <el-table-column prop="co_no" label="单号" min-width="150" />
            <el-table-column prop="customer_name" label="客户" min-width="100" show-overflow-tooltip />
            <el-table-column label="进度" width="90" align="right">
              <template #default="{ row }"><small class="text-muted">{{ row.delivered_quantity }}/{{ row.total_quantity }}</small></template>
            </el-table-column>
            <template #empty><el-empty description="暂无" :image-size="40" /></template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 库存预警 + 最近订单 + 最近送货 -->
    <el-row :gutter="8">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="head-title"><el-icon><WarningFilled /></el-icon> 库存预警</span>
              <router-link to="/stock/inventory" class="head-link">查看</router-link>
            </div>
          </template>
          <el-table :data="data.alerts" size="small" max-height="200">
            <el-table-column prop="name" label="物料" min-width="110" show-overflow-tooltip />
            <el-table-column prop="stock" label="库存" width="70" align="right">
              <template #default="{ row }"><span class="text-danger">{{ row.stock }}</span></template>
            </el-table-column>
            <el-table-column prop="min_stock" label="下限" width="60" align="right" />
            <template #empty><el-empty description="暂无 ✅" :image-size="40" /></template>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="head-title"><el-icon><ShoppingCart /></el-icon> 最近订单</span>
              <router-link to="/orders" class="head-link">查看</router-link>
            </div>
          </template>
          <el-table :data="data.recent_orders" size="small" max-height="200">
            <el-table-column prop="customer_name" label="客户" min-width="90" show-overflow-tooltip />
            <el-table-column prop="total_amount" label="金额" width="90" align="right">
              <template #default="{ row }">¥{{ fmt(row.total_amount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="60" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'pending' ? 'warning' : row.status === 'partial' ? 'primary' : 'success'" size="small">
                  {{ row.status === 'pending' ? '待' : row.status === 'partial' ? '部分' : '完' }}
                </el-tag>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无" :image-size="40" /></template>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="head-title"><el-icon><Van /></el-icon> 最近送货</span>
              <router-link to="/deliveries" class="head-link">查看</router-link>
            </div>
          </template>
          <el-table :data="data.recent_deliveries" size="small" max-height="200">
            <el-table-column prop="customer_name" label="客户" min-width="90" show-overflow-tooltip />
            <el-table-column prop="dn_date" label="日期" width="95" />
            <el-table-column prop="total_amount" label="金额" width="90" align="right">
              <template #default="{ row }">¥{{ fmt(row.total_amount) }}</template>
            </el-table-column>
            <template #empty><el-empty description="暂无" :image-size="40" /></template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'

const data = ref({
  stats: {},
  payables: [],
  receivables: [],
  pending_list: [],
  partial_list: [],
  alerts: [],
  recent_orders: [],
  recent_deliveries: [],
})

function fmt(v) {
  return Number(v || 0).toLocaleString(undefined, { maximumFractionDigits: 0 })
}

onMounted(async () => {
  try {
    const res = await request.get('/dashboard/data')
    data.value = res.data
  } catch { /* 忽略 */ }
})
</script>

<style scoped>
.dash-wrap { padding: 2px; }
.mb-2 { margin-bottom: 10px; }
.dash-stat { border-radius: 8px; padding: 14px 16px; color: #fff; margin-bottom: 2px; }
.dash-stat .num { font-size: 24px; font-weight: bold; line-height: 1.2; }
.dash-stat .lbl { font-size: 12px; opacity: .85; display: flex; align-items: center; gap: 4px; }
.dash-stat .sub { font-size: 11px; opacity: .6; margin-top: 3px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.head-title { font-size: 12px; font-weight: 600; display: flex; align-items: center; gap: 4px; }
.head-link { font-size: 11px; color: #409eff; text-decoration: none; }
.col-title { font-size: 10px; color: #888; padding: 2px 0; }
.row-line { display: flex; justify-content: space-between; font-size: 11px; padding: 1px 2px; border-bottom: 1px solid #f5f5f5; }
.line-name { max-width: 60%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.line-amt { font-weight: bold; }
.text-danger { color: #f56c6c; }
.text-success { color: #67c23a; }
.text-muted { color: #909399; font-size: 11px; padding: 4px 2px; }
@media (max-width: 767px) {
  .dash-stat { padding: 10px; }
  .dash-stat .num { font-size: 17px; }
  .dash-stat .lbl { font-size: 11px; }
}
</style>
