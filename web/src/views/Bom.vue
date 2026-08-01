<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="BOM 配方管理">
        <template #icon><Connection /></template>
        <div class="hint">成品 → 组件用料配方；保存后送货出库时可自动扣组件库存</div>
      </PageHeader>
    </template>

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索成品料号/名称" clearable size="small" style="width: 220px"
                @keyup.enter="load(1)" @clear="load(1)" />
      <el-button type="primary" size="small" @click="load(1)">查询</el-button>
      <el-button size="small" @click="openEdit(null)"><el-icon><Plus /></el-icon> 新建 BOM</el-button>
    </div>

    <el-table :data="items" size="small" stripe v-loading="loading" v-if="!isMobile">
      <el-table-column prop="product_code" label="成品料号" min-width="120" />
      <el-table-column prop="product_name" label="成品名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="product_spec" label="规格" min-width="120" show-overflow-tooltip />
      <el-table-column prop="component_count" label="组件数" width="90" align="right" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="clear(row)">清空</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无 BOM，点击右上「新建 BOM」配置" :image-size="70" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.product_name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>成品名称</span><b>{{ row.product_name }}</b></div>
          <div class="m-row"><span>料号</span><b>{{ row.product_code }}</b></div>
          <div class="m-row"><span>规格</span><b>{{ row.product_spec }}</b></div>
          <div class="m-row"><span>组件数</span><b>{{ row.component_count }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button><el-button link type="primary" size="small" @click.stop="clear(row)">清空</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="size" :current-page="page" @current-change="load" />

    <!-- 编辑 BOM 弹窗 -->
    <el-dialog v-model="editVisible" :title="editForm.productId ? '编辑 BOM' : '新建 BOM'" width="820px">
      <el-form ref="formRef" :rules="formRules" label-width="80px" size="small">
        <el-form-item label="成品" required prop="productId">
          <el-select v-model="editForm.productId" filterable placeholder="选择成品物料" style="width: 100%"
                     :disabled="!!editForm.productId" @change="loadDetail">
            <el-option v-for="m in productOptions" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="组件明细">
          <el-table :data="editForm.rows" size="small" border max-height="300">
            <el-table-column label="组件物料" min-width="220">
              <template #default="{ row }">
                <el-select v-model="row.componentId" filterable placeholder="选择组件物料" style="width: 100%" size="small">
                  <el-option v-for="m in componentOptions" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="用量" width="130">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0.001" :precision="3" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="" width="60">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="editForm.rows.splice($index, 1)">✕</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <div class="row-add">
          <el-button size="small" @click="addRow"><el-icon><Plus /></el-icon> 添加组件</el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
const formRef = ref(null)
const formRules = {
  productId: [{ required: true, message: '请填写成品', trigger: 'change' }],
}
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const editVisible = ref(false)
const productOptions = ref([])
const componentOptions = ref([])
const editForm = reactive({ productId: null, rows: [] })

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await request.get('/bom', { params: { keyword: keyword.value, page: page.value, size: size.value } })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [p, c] = await Promise.all([
      request.get('/materials', { params: { page: 1, size: 100, keyword: '成品' } }),
      request.get('/materials', { params: { page: 1, size: 100 } }),
    ])
    productOptions.value = p.data.items
    componentOptions.value = c.data.items
  } catch { /* 忽略 */ }
}

function openEdit(row) {
  editForm.productId = row ? row.product_id : null
  editForm.rows = []
  editVisible.value = true
  if (row) loadDetail(row.product_id)
}

async function loadDetail(pid) {
  if (!pid) return
  const res = await request.get(`/bom/${pid}`)
  editForm.rows = res.data.items.map((x) => ({ componentId: x.component_id, quantity: Number(x.quantity) }))
  if (!editForm.rows.length) addRow()
}

function addRow() {
  editForm.rows.push({ componentId: null, quantity: 1 })
}

async function save() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!editForm.productId) {
    ElMessage.warning('请选择成品物料')
    return
  }
  const rows = editForm.rows.filter((r) => r.componentId)
  if (!rows.length) {
    ElMessage.warning('请至少添加一个组件')
    return
  }
  saving.value = true
  try {
    await request.post(`/bom/${editForm.productId}`, { items: rows })
    ElMessage.success('BOM 已保存')
    editVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function clear(row) {
  try {
    await ElMessageBox.confirm(`确定清空「${row.product_name}」的 BOM 配方？`, '清空确认', { type: 'warning' })
  } catch { return }
  try {
    await request.delete(`/bom/${row.product_id}`)
    ElMessage.success('已清空')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => { load(1); loadOptions() })
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; }


.row-add { text-align: center; }

/* 手机卡片 */






.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }


</style>
