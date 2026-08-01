<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <PageHeader title="列表页标题" desc="功能说明" />
      </template>

      <!-- 搜索栏（FilterBar 或自定义） -->
      <div class="search-bar">
        <el-input v-model="kw" placeholder="搜索关键词" clearable size="small" style="width: 200px"
                  @keyup.enter="load(1)" @clear="load(1)" />
        <el-button type="primary" size="small" @click="load(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button type="success" size="small" @click="openForm()"><el-icon><Plus /></el-icon> 新增</el-button>
      </div>

      <!-- 桌面表格 -->
      <el-table :data="items" v-loading="loading" size="small" stripe v-if="!isMobile">
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无数据" :image-size="60" /></template>
      </el-table>

      <!-- 手机卡片 -->
      <div v-else class="m-cards">
        <div v-for="row in items" :key="row.id" class="m-card">
          <div class="m-card-head">
            <span class="m-name">{{ row.name }}</span>
          </div>
          <div class="m-card-body">
            <div class="m-row"><span>说明</span><b>{{ row.remark || '—' }}</b></div>
          </div>
          <div class="m-actions">
            <el-button link type="primary" size="small" @click.stop="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
          </div>
        </div>
        <div v-if="!items.length" class="m-empty">暂无数据</div>
      </div>

      <!-- 分页 -->
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                     :page-size="size" :current-page="page" @current-change="load" />

      <!-- 新增/编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="form.id ? '编辑' : '新增'" width="480px">
        <el-form ref="formRef" :rules="formRules" :model="form" label-width="80px" size="small">
          <el-form-item label="名称" prop="name" required>
            <el-input v-model="form.name" placeholder="请输入名称" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button size="small" @click="dialogVisible = false">取消</el-button>
          <el-button size="small" type="primary" @click="save">保存</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

/* ===== 新增功能模板说明 =====
 * 复制本文件到 views/XxxXxx.vue，改三处：
 * 1. API 路径：下方 API_PREFIX（后端 BaseController 继承的 Controller）
 * 2. 表格列 / 表单字段：替换成新功能的字段
 * 3. 路由：router/index.js 加一行 + 后端菜单 MENU_TREE 加菜单项（自动出现）
 * ======================== */
const API_PREFIX = '/demo' // ← 改成新功能后端路径

const isMobile = ref(window.innerWidth <= 767)
const items = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const kw = ref('')
const dialogVisible = ref(false)

const form = reactive({ id: null, name: '', remark: '' })
const formRef = ref(null)
const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await request.get(API_PREFIX, { params: { keyword: kw.value, page: page.value, size: size.value } })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  Object.assign(form, row ? { id: row.id, name: row.name, remark: row.remark || '' } : { id: null, name: '', remark: '' })
  dialogVisible.value = true
}

async function save() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  try {
    if (form.id) {
      await request.put(`${API_PREFIX}/${form.id}`, form)
    } else {
      await request.post(API_PREFIX, form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await request.delete(`${API_PREFIX}/${row.id}`)
    ElMessage.success('删除成功')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => load())
</script>
