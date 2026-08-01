<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="库存查询">
        <template #icon><Box /></template>
      </PageHeader>
    </template>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索名称/编号/规格/分类"
        clearable
        style="width: 260px"
        @keyup.enter="load(1)"
        @clear="load(1)"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" size="small" @click="load(1)">搜索</el-button>
      <el-button size="small" @click="resetSearch">重置</el-button>
      <el-select v-model="warehouseId" placeholder="全部仓库" clearable size="small" style="width: 140px"
                 @change="load(1)">
        <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
      <el-switch v-model="onlyLow" active-text="只看低库存" @change="load(1)" style="margin-left: 8px" />
    </div>

    <!-- 表格（桌面） -->
    <el-table v-if="!isMobile" :data="visibleItems" v-loading="loading" size="small" stripe highlight-current-row
              @row-click="showMovements">
      <el-table-column prop="code" label="编号" width="110" show-overflow-tooltip />
      <el-table-column prop="name" label="物料名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="spec" label="规格" min-width="130" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="60" />
      <el-table-column prop="category" label="分类" width="90" show-overflow-tooltip />
      <el-table-column prop="stock" label="当前库存" width="100" align="right" sortable>
        <template #default="{ row }">
          <span :class="isLow(row) ? 'low-stock' : ''">{{ fmt(row.stock) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="min_stock" label="最低库存" width="90" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.min_stock) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="isLow(row)" type="danger" size="small">⚠ 偏低</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="purchase_price" label="采购价" width="90" align="right"  sortable/>
      <el-table-column prop="sale_price" label="销售价" width="90" align="right"  sortable/>
      <template #empty><el-empty description="暂无物料" :image-size="60" /></template>
    </el-table>

    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in visibleItems" :key="row.id" class="m-card" @click="showMovements(row)">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
          <el-tag v-if="isLow(row)" type="danger" size="small">⚠ 偏低</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>编号</span><b>{{ row.code }}</b></div>
          <div class="m-row"><span>规格</span><b>{{ row.spec }}</b></div>
          <div class="m-row"><span>单位/分类</span><b>{{ row.unit }} / {{ row.category }}</b></div>
          <div class="m-row"><span>当前库存</span><b :class="isLow(row) ? 'low-stock' : ''">{{ fmt(row.stock) }}</b></div>
          <div class="m-row"><span>最低库存</span><b>{{ fmt(row.min_stock) }}</b></div>
          <div class="m-row"><span>采购价</span><b>￥{{ fmt(row.purchase_price) }}</b></div>
          <div class="m-row"><span>销售价</span><b>￥{{ fmt(row.sale_price) }}</b></div>
        </div>
      </div>
      <div v-if="!visibleItems.length" class="m-empty">暂无物料</div>
    </div>

    <!-- 分页 -->
    <el-pagination
      class="pager"
      background
      layout="total, prev, pager, next, sizes"
      :total="total"
      :page-size="size"
      :current-page="page"
      :page-sizes="[20, 50, 100]"
      @current-change="load"
      @size-change="onSizeChange"
    />

    <!-- 库存流水弹窗 -->
    <el-dialog v-model="mvVisible" :title="'库存流水 — ' + (curMaterial?.name || '')" width="820px">
      <el-table :data="movements" size="small" max-height="420">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="move_date" label="日期" width="110"  sortable/>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.move_type === 'in' ? 'success' : 'danger'" size="small">
              {{ row.move_type === 'in' ? '入库' : '出库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ref_type" label="来源" width="120" />
        <el-table-column prop="quantity" label="数量" width="90" align="right"  sortable/>
        <el-table-column prop="before_stock" label="前库存" width="90" align="right" />
        <el-table-column prop="after_stock" label="后库存" width="90" align="right" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <template #empty><el-empty description="暂无流水" :image-size="50" /></template>
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import { stockApi as api } from '../api/stock'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const onlyLow = ref(false)
const warehouseId = ref(null)
const warehouses = ref([])
const loading = ref(false)
const mvVisible = ref(false)
const movements = ref([])
const curMaterial = ref(null)
const isMobile = ref(window.innerWidth <= 767)

window.addEventListener('resize', () => {
  isMobile.value = window.innerWidth <= 767
})

const visibleItems = computed(() =>
  onlyLow.value ? items.value.filter(isLow) : items.value
)

function isLow(row) {
  return Number(row.stock) <= Number(row.min_stock)
}
function fmt(v) {
  return v === null || v === undefined ? '0' : Number(v).toLocaleString()
}

function onSizeChange(s) {
  size.value = s
  load(1)
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const params = { keyword: keyword.value, page: page.value, size: size.value }
    if (warehouseId.value) params.warehouseId = warehouseId.value
    const res = await api.inventory(params)
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadWarehouses() {
  try {
    const res = await api.warehouses({ size: 100 })
    warehouses.value = res.data.items || []
  } catch { /* 忽略 */ }
}

function resetSearch() {
  keyword.value = ''
  onlyLow.value = false
  warehouseId.value = null
  load(1)
}

async function showMovements(row) {
  curMaterial.value = row
  try {
    const res = await api.movements({ materialId: row.id, limit: 200 })
    movements.value = res.data.items
    mvVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => { load(1); loadWarehouses() })
</script>

<style scoped>


.low-stock {
  color: #f56c6c;
  font-weight: 700;
}
/* 手机卡片 */






.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }

</style>
