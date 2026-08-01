<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="客户管理">
        <template #icon><User /></template>
        <el-button type="primary" size="small" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新增客户
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
      <el-table-column prop="name" label="客户名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="contact" label="联系人" width="100" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="region" label="区域" width="100" />
      <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无客户" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>客户名称</span><b>{{ row.name }}</b></div>
          <div class="m-row"><span>编号</span><b>{{ row.code }}</b></div>
          <div class="m-row"><span>联系人</span><b>{{ row.contact }}</b></div>
          <div class="m-row"><span>电话</span><b>{{ row.phone }}</b></div>
          <div class="m-row"><span>区域</span><b>{{ row.region }}</b></div>
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
      @size-change="(s) => { size = s; load(1) }"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑客户' : '新增客户'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编号"><el-input v-model="form.code" placeholder="客户编号（可选）" /></el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="客户名称" />
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="区域"><el-select v-model="form.region" filterable allow-create default-first-option clearable placeholder="选择或输入区域" style="width: 100%">
          <el-option v-for="r in regionOptions" :key="r" :label="r" :value="r" />
        </el-select></el-form-item>
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
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, code: '', name: '', contact: '', phone: '', region: '', address: '', bankAccount: '', taxId: '', remark: '' })
const regionOptions = ref([])

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await api.customers({
      params: { keyword: keyword.value, page: page.value, size: size.value },
    })
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
  Object.assign(form, row ? { ...row } : { id: null, code: '', name: '', contact: '', phone: '', region: '', address: '', bankAccount: '', taxId: '', remark: '' })
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写客户名称')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await api.updateCustomer(form.id, form)
    } else {
      await api.createCustomer(form)
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
    await ElMessageBox.confirm(`确定删除客户「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteCustomer(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  load(1)
  try {
    const d = await request.get('/dicts')
    regionOptions.value = (d.data.dicts.customer_regions || []).map(x => x.label)
  } catch (e) { /* 字典加载失败用空列表 */ }
})
</script>

<style scoped>
.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

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
