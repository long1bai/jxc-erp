<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="快递物流">
        <template #icon><Van /></template>
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 新增物流公司
        </el-button>
      </PageHeader>
    </template>

    <el-table :data="items" size="small" stripe v-loading="loading" v-if="!isMobile">
      <el-table-column prop="name" label="物流公司" min-width="160" />
      <el-table-column prop="contact" label="联系人" width="110" />
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无物流公司" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>物流公司</span><b>{{ row.name }}</b></div>
          <div class="m-row"><span>联系人</span><b>{{ row.contact }}</b></div>
          <div class="m-row"><span>电话</span><b>{{ row.phone }}</b></div>
          <div class="m-row"><span>备注</span><b>{{ row.remark }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="openEdit(row)">{{ 编辑 }}</el-button><el-button link type="primary" size="small" @click.stop="remove(row)">{{ 删除 }}</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>

    <el-dialog v-model="visible" :title="editing ? '编辑物流公司' : '新增物流公司'" width="480px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：顺丰速运、德邦物流" size="small" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contact" size="small" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" size="small" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="visible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Van, Plus } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const visible = ref(false)
const editing = ref(false)
const form = reactive({ id: null, name: '', contact: '', phone: '', remark: '' })

async function load() {
  loading.value = true
  try {
    const r = await request.get('/express-companies')
    items.value = r.data.items
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, name: '', contact: '', phone: '', remark: '' })
  visible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, { id: row.id, name: row.name, contact: row.contact || '', phone: row.phone || '', remark: row.remark || '' })
  visible.value = true
}

async function save() {
  if (!form.name.trim()) return alert('请填写物流公司名称')
  saving.value = true
  try {
    const payload = { name: form.name, contact: form.contact, phone: form.phone, remark: form.remark }
    const r = editing.value
      ? await request.put(`/express-companies/${form.id}`, payload)
      : await request.post('/express-companies', payload)
    if (!r.data?.success) return alert(r.data?.error || '保存失败')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  if (!confirm(`确认删除物流公司「${row.name}」？`)) return
  await request.delete(`/express-companies/${row.id}`)
  load()
}

onMounted(load)
</script>
