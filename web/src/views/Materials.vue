<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="物料管理">
        <template #icon><Box /></template>
        <el-button type="primary" size="small" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新增物料
        </el-button>
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
    </div>

    <!-- 表格 -->
    <el-table :data="items" v-loading="loading" size="small" stripe v-if="!isMobile">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="编号" width="110" show-overflow-tooltip />
      <el-table-column prop="name" label="物料名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="spec" label="规格" min-width="140" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column prop="category" label="分类" width="100" show-overflow-tooltip />
      <el-table-column prop="purchasePrice" label="采购价" width="90" align="right"  sortable/>
      <el-table-column prop="salePrice" label="销售价" width="90" align="right"  sortable/>
      <el-table-column prop="minStock" label="最低库存" width="90" align="right"  sortable/>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无物料" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>物料名称</span><b>{{ row.name }}</b></div>
          <div class="m-row"><span>编号</span><b>{{ row.code }}</b></div>
          <div class="m-row"><span>规格</span><b>{{ row.spec }}</b></div>
          <div class="m-row"><span>单位</span><b>{{ row.unit }}</b></div>
          <div class="m-row"><span>分类</span><b>{{ row.category }}</b></div>
          <div class="m-row"><span>采购价</span><b>{{ '￥' + (row.purchasePrice ?? 0) }}</b></div>
          <div class="m-row"><span>销售价</span><b>{{ '￥' + (row.salePrice ?? 0) }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="openDialog(row)">{{ 编辑 }}</el-button><el-button link type="primary" size="small" @click.stop="remove(row)">{{ 删除 }}</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>

    <!-- 分页 -->
    <el-pagination
      class="pager"
      background
      layout="total, prev, pager, next, sizes"
      :total="total"
      :page-size="size"
      :current-page="page"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="load"
      @size-change="onSizeChange"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑物料' : '新增物料'" width="640px">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="90px">
        <el-form-item label="编号"><el-input v-model="form.code" placeholder="物料编号（可选）" /></el-form-item>
        <el-form-item label="名称" required prop="name">
          <el-input v-model="form.name" placeholder="物料名称" />
        </el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" placeholder="如 18AWG 红" /></el-form-item>
        <el-row :gutter="8">
          <el-col :span="8">
            <el-form-item label="单位"><el-input v-model="form.unit" placeholder="个/米/卷" /></el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="分类"><el-input v-model="form.category" placeholder="如 线材/端子/成品" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="8">
          <el-col :span="8">
            <el-form-item label="采购价"><el-input v-model="form.purchasePrice" type="number" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销售价"><el-input v-model="form.salePrice" type="number" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最低库存"><el-input v-model="form.minStock" type="number" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { baseApi as api } from '../api/base'

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
const formRef = ref(null)
const formRules = {
  name: [{ required: true, message: '请填写名称', trigger: 'change' }],
}
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, code: '', name: '', spec: '', unit: '', category: '', purchasePrice: null, salePrice: null, minStock: null, remark: '' })

function onSizeChange(s) {
  size.value = s
  load(1)
}

async function load(p) {
  if (p) page.value = p
  console.log('[Materials] load 请求 page =', page.value, 'size =', size.value)
  loading.value = true
  try {
    const res = await api.materials({ keyword: keyword.value, page: page.value, size: size.value })
    items.value = res.data.items
    total.value = Number(res.data.total)
    console.log('[Materials] 返回 items 首条 id =', items.value[0]?.id, '条数 =', items.value.length, 'total =', total.value)
  } catch (e) {
    console.log('[Materials] 请求失败:', e.message)
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  keyword.value = ''
  load(1)
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : { id: null, code: '', name: '', spec: '', unit: '', category: '', purchasePrice: null, salePrice: null, minStock: null, remark: '' })
  dialogVisible.value = true
}

async function save() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!form.name.trim()) {
    ElMessage.warning('请填写物料名称')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await api.updateMaterial(form.id, form)
    } else {
      await api.createMaterial(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除物料「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteMaterial(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => load(1))
</script>

<style scoped>



/* 手机卡片 */






.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }


</style>
