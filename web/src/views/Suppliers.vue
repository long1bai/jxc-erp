<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="供应商管理">
        <template #icon><OfficeBuilding /></template>
        <el-button type="primary" size="small" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新增供应商
        </el-button>
      </PageHeader>
    </template>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索名称/编号/联系人/电话"
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
      <el-table-column prop="code" label="编号" width="110" />
      <el-table-column prop="name" label="供应商名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="contact" label="联系人" width="100" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无供应商" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>供应商名称</span><b>{{ row.name }}</b></div>
          <div class="m-row"><span>编号</span><b>{{ row.code }}</b></div>
          <div class="m-row"><span>联系人</span><b>{{ row.contact }}</b></div>
          <div class="m-row"><span>电话</span><b>{{ row.phone }}</b></div>
          <div class="m-row"><span>地址</span><b>{{ row.address }}</b></div>
          <div class="m-row"><span>备注</span><b>{{ row.remark }}</b></div>
        </div>
        <div class="m-actions">
          <el-button link type="primary" size="small" @click.stop="openDialog(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
        </div>
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
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑供应商' : '新增供应商'" width="480px">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="80px">
        <el-form-item label="编号"><el-input v-model="form.code" placeholder="供应商编号（可选）" /></el-form-item>
        <el-form-item label="名称" required prop="name">
          <el-input v-model="form.name" placeholder="供应商名称" />
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="开户行账号"><el-input v-model="form.bankAccount" /></el-form-item>
        <el-form-item label="税号"><el-input v-model="form.taxId" /></el-form-item>
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
const form = reactive({ id: null, code: '', name: '', contact: '', phone: '', address: '', bankAccount: '', taxId: '', remark: '' })

function onSizeChange(s) {
  size.value = s
  load(1)
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await api.suppliers({ keyword: keyword.value, page: page.value, size: size.value })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
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
  Object.assign(form, row ? { ...row } : { id: null, code: '', name: '', contact: '', phone: '', address: '', bankAccount: '', taxId: '', remark: '' })
  dialogVisible.value = true
}

async function save() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!form.name.trim()) {
    ElMessage.warning('请填写供应商名称')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await api.updateSupplier(form.id, form)
    } else {
      await api.createSupplier(form)
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
    await ElMessageBox.confirm(`确定删除供应商「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteSupplier(row.id)
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
