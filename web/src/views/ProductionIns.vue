<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="成品入库">
        <template #icon><Box /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建入库单
        </el-button>
      </PageHeader>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
              title="生产完成的产品入库（生成 in 流水，自动加成品库存）。材料已在报工时按 BOM 自动扣减，此处不重复扣料。" />

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索入库单号" clearable size="small" style="width: 200px"
                @keyup.enter="load(1)" @clear="load(1)" />
      <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
    </div>

    <el-table :data="items" size="small" stripe v-loading="loading" max-height="480" v-if="!isMobile">
      <el-table-column prop="pi_no" label="入库单号" min-width="160" />
      <el-table-column prop="in_date" label="日期" width="100" sortable />
      <el-table-column prop="total_items" label="产品数" width="80" align="right" sortable />
      <el-table-column prop="total_quantity" label="入库数量" width="110" align="right" sortable />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="view(row)">明细</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无入库单" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.pi_no }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>入库单号</span><b>{{ row.pi_no }}</b></div>
          <div class="m-row"><span>日期</span><b>{{ row.in_date }}</b></div>
          <div class="m-row"><span>产品数</span><b>{{ row.total_items }}</b></div>
          <div class="m-row"><span>入库数量</span><b>{{ row.total_quantity }}</b></div>
          <div class="m-row"><span>备注</span><b>{{ row.remark }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="view(row)">{{ 明细 }}</el-button><el-button link type="primary" size="small" @click.stop="remove(row)">{{ 删除 }}</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="load" />

    <el-dialog v-model="createVisible" title="新建成品入库单" width="720px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item label="日期">
          <el-date-picker v-model="form.inDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="产品明细">
          <el-table :data="form.items" size="small" border max-height="300">
            <el-table-column label="产品" min-width="240">
              <template #default="{ row }">
                <el-select v-model="row.productId" filterable placeholder="选择产品" style="width: 100%" size="small">
                  <el-option v-for="m in products" :key="m.id" :label="`${m.code || ''} ${m.name}`" :value="m.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0.001" :precision="3" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column width="60" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="form.items.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="form.items.push({ productId: null, quantity: 1 })"><el-icon><Plus /></el-icon> 添加产品</el-button>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewVisible" title="入库单明细" width="640px">
      <el-descriptions :column="3" size="small" border style="margin-bottom: 10px">
        <el-descriptions-item label="入库单号">{{ viewMain.pi_no }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ viewMain.in_date }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ viewMain.total_quantity }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="viewItems" size="small" border>
        <el-table-column prop="product_name" label="产品" min-width="160" />
        <el-table-column prop="spec" label="规格" min-width="120" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="amount" label="金额" width="110" align="right" />
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Box, Plus } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const products = ref([])
const createVisible = ref(false)
const viewVisible = ref(false)
const viewMain = ref({})
const viewItems = ref([])
const form = reactive({ inDate: '', remark: '', items: [] })

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const r = await request.get('/production-ins', { params: { keyword: keyword.value, page: page.value, size: size.value } })
    items.value = r.data.items
    total.value = r.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.inDate = new Date().toISOString().slice(0, 10)
  form.remark = ''
  form.items = [{ productId: null, quantity: 1 }]
  createVisible.value = true
}

async function save() {
  if (!form.items.length || form.items.some(i => !i.productId)) return alert('请填写产品明细')
  saving.value = true
  try {
    const r = await request.post('/production-ins', form)
    if (!r.data?.success) return alert(r.data?.error || '保存失败')
    createVisible.value = false
    load(1)
  } finally {
    saving.value = false
  }
}

async function view(row) {
  const r = await request.get(`/production-ins/${row.id}`)
  viewMain.value = r.data.main
  viewItems.value = r.data.items
  viewVisible.value = true
}

async function remove(row) {
  if (!confirm(`确认删除入库单 ${row.pi_no}？库存将自动回退。`)) return
  await request.delete(`/production-ins/${row.id}`)
  load(page.value)
}

onMounted(async () => {
  load(1)
  const r = await request.get('/materials?size=5000')
  products.value = r.data.items
})
</script>

<style scoped>
.search-bar { display: flex; gap: 8px; margin-bottom: 10px; }
.pager { margin-top: 10px; justify-content: flex-end; }

/* 手机卡片 */
.m-cards { display: flex; flex-direction: column; gap: 10px; }
.m-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 10px 12px; box-shadow: 0 1px 2px rgba(0,0,0,.04);
}
.m-card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.m-name { font-size: 15px; font-weight: 600; color: #303133; }
.m-card-body { display: flex; flex-direction: column; gap: 4px; }
.m-row { display: flex; justify-content: space-between; font-size: 13px; }
.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }
.m-empty { text-align: center; color: #909399; padding: 30px 0; font-size: 13px; }
.m-actions { display: flex; justify-content: flex-end; gap: 4px; margin-top: 6px; }
</style>
