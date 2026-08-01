<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="仓库管理">
        <template #icon><OfficeBuilding /></template>
        <el-button type="primary" size="small" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新增仓库
        </el-button>
      </PageHeader>
    </template>

    <el-table :data="items" v-loading="loading" size="small" stripe v-if="!isMobile">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="仓库名称" min-width="160" />
      <el-table-column prop="location" label="位置" min-width="160" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无仓库" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>仓库名称</span><b>{{ row.name }}</b></div>
          <div class="m-row"><span>位置</span><b>{{ row.location }}</b></div>
          <div class="m-row"><span>备注</span><b>{{ row.remark }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="openDialog(row)">编辑</el-button><el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑仓库' : '新增仓库'" width="480px">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="70px">
        <el-form-item label="名称" required prop="name"><el-input v-model="form.name" placeholder="如：原料仓 / 成品仓" /></el-form-item>
        <el-form-item label="位置"><el-input v-model="form.location" placeholder="如：1楼东侧（可选）" /></el-form-item>
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
import request from '../utils/request'

const items = ref([])
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
const formRef = ref(null)
const formRules = {
  name: [{ required: true, message: '请填写名称', trigger: 'change' }],
}
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, name: '', location: '', remark: '' })

async function load() {
  loading.value = true
  try {
    const res = await request.get('/warehouses', { params: { size: 100 } })
    items.value = res.data.items
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : { id: null, name: '', location: '', remark: '' })
  dialogVisible.value = true
}

async function save() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!form.name.trim()) {
    ElMessage.warning('请填写仓库名称')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await request.put(`/warehouses/${form.id}`, form)
    } else {
      await request.post('/warehouses', form)
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
    await ElMessageBox.confirm(`确定删除仓库「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await request.delete(`/warehouses/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>

/* 手机卡片 */






.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }


</style>
