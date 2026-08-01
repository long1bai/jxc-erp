<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="库存盘点">
        <template #icon><Box /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建盘点单
        </el-button>
      </PageHeader>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
              title="流程：新建盘点单 → 系统自动带出账面库存 → 录入实盘数量 → 确认后自动调整库存（盘盈加/盘亏减）。期初库存 = 第一次盘点录入实盘数即建立。" />

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索盘点单号" clearable size="small" style="width: 200px"
                @keyup.enter="load(1)" @clear="load(1)" />
      <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
    </div>

    <el-table :data="items" size="small" stripe v-loading="loading" max-height="480" v-if="!isMobile">
      <el-table-column prop="st_no" label="盘点单号" min-width="150" />
      <el-table-column prop="take_date" label="日期" width="100" sortable />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'done' ? 'success' : 'warning'" size="small">
            {{ row.status === 'done' ? '已确认' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="total_items" label="物料数" width="80" align="right" sortable />
      <el-table-column prop="total_diff_qty" label="差异数量" width="100" align="right" sortable>
        <template #default="{ row }">{{ fmtQty(row.total_diff_qty) }}</template>
      </el-table-column>
      <el-table-column prop="total_diff_amount" label="差异金额" width="120" align="right" sortable>
        <template #default="{ row }">￥{{ fmt(row.total_diff_amount) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="view(row)">明细</el-button>
          <el-button v-if="row.status !== 'done'" link type="success" size="small" @click="confirm(row)">确认</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无盘点单" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.st_no }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>盘点单号</span><b>{{ row.st_no }}</b></div>
          <div class="m-row"><span>日期</span><b>{{ row.take_date }}</b></div>
          <div class="m-row"><span>物料数</span><b>{{ row.total_items }}</b></div>
          <div class="m-row"><span>差异数量</span><b>{{ row.total_diff_qty }}</b></div>
          <div class="m-row"><span>差异金额</span><b>{{ '￥' + (row.total_diff_amount ?? 0) }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="view(row)">{{ 明细 }}</el-button><el-button link type="primary" size="small" @click.stop="remove(row)">{{ 删除 }}</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="load" />

    <!-- 新建盘点单弹窗 -->
    <el-dialog v-model="createVisible" title="新建盘点单" width="820px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item label="日期">
          <el-date-picker v-model="form.takeDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="盘点物料">
          <el-select v-model="form.materialIds" multiple filterable placeholder="选择要盘点的物料（可多选）" style="width: 100%">
            <el-option v-for="m in materials" :key="m.id" :label="`${m.code || ''} ${m.name}`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="实盘数量">
          <el-table :data="form.rows" size="small" border max-height="320">
            <el-table-column prop="name" label="物料" min-width="180" show-overflow-tooltip />
            <el-table-column prop="code" label="编码" min-width="120" />
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column label="账面库存" width="100" align="right">
              <template #default="{ row }">{{ fmtQty(row.bookQty) }}</template>
            </el-table-column>
            <el-table-column label="实盘数量" width="130">
              <template #default="{ row }">
                <el-input-number v-model="row.actualQty" :min="0" :precision="3" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="差异" width="100" align="right">
              <template #default="{ row }">
                <span :style="{ color: diffOf(row) > 0 ? '#e6a23c' : diffOf(row) < 0 ? '#f56c6c' : '#999' }">
                  {{ fmtQty(diffOf(row)) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 明细弹窗 -->
    <el-dialog v-model="viewVisible" title="盘点单明细" width="760px">
      <el-descriptions :column="3" size="small" border style="margin-bottom: 10px">
        <el-descriptions-item label="盘点单号">{{ viewMain.st_no }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ viewMain.take_date }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ viewMain.status === 'done' ? '已确认' : '草稿' }}</el-descriptions-item>
        <el-descriptions-item label="物料数">{{ viewMain.total_items }}</el-descriptions-item>
        <el-descriptions-item label="差异数量">{{ fmtQty(viewMain.total_diff_qty) }}</el-descriptions-item>
        <el-descriptions-item label="差异金额">￥{{ fmt(viewMain.total_diff_amount) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="viewItems" size="small" border max-height="320">
        <el-table-column prop="material_name" label="物料" min-width="160" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" min-width="120" />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="book_qty" label="账面" width="90" align="right" />
        <el-table-column prop="actual_qty" label="实盘" width="90" align="right" />
        <el-table-column label="差异" width="90" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.diff_qty) > 0 ? '#e6a23c' : Number(row.diff_qty) < 0 ? '#f56c6c' : '#999' }">
              {{ fmtQty(row.diff_qty) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="diff_amount" label="差异金额" width="100" align="right" />
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
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
const materials = ref([])
const bookMap = ref({})
const createVisible = ref(false)
const viewVisible = ref(false)
const viewMain = ref({})
const viewItems = ref([])
const form = reactive({ takeDate: '', materialIds: [], remark: '', rows: [] })

const materialMap = () => Object.fromEntries(materials.value.map(m => [m.id, m]))

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtQty(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 3 })
}
function diffOf(row) {
  return Number(row.actualQty ?? 0) - Number(row.bookQty ?? 0)
}

watch(() => form.materialIds, (ids) => {
  const mm = materialMap()
  const book = bookMap.value
  form.rows = ids
    .map(id => mm[id])
    .filter(Boolean)
    .map(m => ({ materialId: m.id, code: m.code, name: m.name, unit: m.unit, bookQty: Number(book[m.id] || 0), actualQty: Number(book[m.id] || 0) }))
})

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const r = await request.get('/stock-takes', { params: { keyword: keyword.value, page: page.value, size: size.value } })
    items.value = r.data.items
    total.value = r.data.total
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  form.takeDate = new Date().toISOString().slice(0, 10)
  form.materialIds = []
  form.remark = ''
  form.rows = []
  createVisible.value = true
}

async function save() {
  if (!form.rows.length) return alert('请选择盘点物料')
  saving.value = true
  try {
    const payload = {
      takeDate: form.takeDate,
      remark: form.remark,
      items: form.rows.map(r => ({ materialId: r.materialId, actualQty: r.actualQty })),
    }
    const r = await request.post('/stock-takes', payload)
    if (!r.data?.success) return alert(r.data?.error || '保存失败')
    createVisible.value = false
    load(1)
  } finally {
    saving.value = false
  }
}

async function view(row) {
  const r = await request.get(`/stock-takes/${row.id}`)
  viewMain.value = r.data.main
  viewItems.value = r.data.items
  viewVisible.value = true
}

async function confirm(row) {
  if (!confirm(`确认盘点单 ${row.st_no}？将按差异调整库存（盘盈加/盘亏减），确认后不可修改。`)) return
  const r = await request.post(`/stock-takes/${row.id}/confirm`)
  if (!r.data?.success) return alert(r.data?.error || '确认失败')
  load(page.value)
}

async function remove(row) {
  if (!confirm(`确认删除盘点单 ${row.st_no}？已调整的库存将自动回退。`)) return
  await request.delete(`/stock-takes/${row.id}`)
  load(page.value)
}

onMounted(async () => {
  load(1)
  const [mr, br] = await Promise.all([
    request.get('/materials?size=5000'),
    request.get('/stock/inventory?size=5000'),
  ])
  materials.value = mr.data.items
  bookMap.value = Object.fromEntries((br.data.items || []).map(i => [i.id, i.stock]))
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
