<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="数据备份">
        <template #icon><FolderOpened /></template>
        <el-button type="primary" size="small" :loading="backing" @click="createBackup">
          <el-icon><Download /></el-icon> 立即备份
        </el-button>
      </PageHeader>
    </template>

    <el-alert type="info" :closable="false" class="mb">
      使用 mysqldump 全量备份数据库，备份文件保存在 I:\yawei-erp-java\backup\ 目录，建议定期备份。
    </el-alert>

    <el-table :data="items" size="small" stripe v-loading="loading" v-if="!isMobile">
      <el-table-column prop="name" label="备份文件" min-width="260" />
      <el-table-column prop="time" label="备份时间" min-width="160" />
      <el-table-column prop="sizeMB" label="大小" width="110" align="right">
        <template #default="{ row }">{{ row.sizeMB.toFixed(2) }} MB</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="download(row)">下载</el-button>
          <el-button link type="danger" size="small" :loading="restoring === row.name" @click="restore(row)">恢复</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无备份记录" :image-size="60" /></template>
    </el-table>
    <!-- 卡片（手机） -->
    <div v-else class="m-cards">
      <div v-for="row in items" :key="row.id" class="m-card">
        <div class="m-card-head">
          <span class="m-name">{{ row.name }}</span>
        </div>
        <div class="m-card-body">
          <div class="m-row"><span>备份文件</span><b>{{ row.name }}</b></div>
          <div class="m-row"><span>备份时间</span><b>{{ row.time }}</b></div>
          <div class="m-row"><span>大小(MB)</span><b>{{ row.sizeMB }}</b></div>
        </div>
        <div class="m-actions"><el-button link type="primary" size="small" @click.stop="download(row)">下载</el-button><el-button link type="primary" size="small" @click.stop="restore(row)">恢复</el-button></div>
      </div>
      <div v-if="!items.length" class="m-empty">暂无数据</div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const items = ref([])
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const backing = ref(false)
const restoring = ref('')

function download(row) {
  window.open(`/api/backup/download/${encodeURIComponent(row.name)}`, '_blank')
}

async function restore(row) {
  if (!confirm(`【危险操作】恢复备份 ${row.name} 将覆盖当前全部数据（用备份时的数据替换现在的数据）。\n\n确定要继续吗？`)) return
  if (!confirm(`最后确认：恢复后当前数据将丢失，无法撤销！\n\n确定恢复备份 ${row.name}？`)) return
  restoring.value = row.name
  try {
    const res = await request.post('/backup/restore', { name: row.name })
    if (!res.data?.success) return ElMessage.error(res.data?.error || '恢复失败')
    ElMessage.success('恢复成功')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    restoring.value = ''
  }
}

async function load() {
  loading.value = true
  try {
    const res = await request.get('/backup/list')
    items.value = res.data.items
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function createBackup() {
  backing.value = true
  try {
    const res = await request.post('/backup/create')
    ElMessage.success(`备份成功：${res.data.file}（${res.data.sizeMB} MB）`)
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    backing.value = false
  }
}

onMounted(() => load())
</script>

<style scoped>


/* 手机卡片 */






.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }


</style>
