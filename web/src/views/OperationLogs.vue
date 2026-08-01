<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <PageHeader title="操作日志" desc="记录所有新增/修改/删除/登录等操作，谁在什么时间做了什么" />
      </template>

      <div class="search-bar">
        <el-input v-model="kw" placeholder="搜索用户/操作/模块" clearable size="small" style="width: 200px"
                  @keyup.enter="load" @clear="load" />
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" size="small"
                        range-separator="~" start-placeholder="开始日期" end-placeholder="结束日期"
                        style="width: 250px" @change="load" />
        <el-button type="primary" size="small" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button size="small" @click="reset">清空</el-button>
        <span class="log-tip">自动记录，保留最近操作</span>
      </div>

      <el-table :data="items" v-loading="loading" size="small" stripe max-height="520">
        <el-table-column prop="created_at" label="时间" width="160">
          <template #default="{ row }">{{ (row.created_at || '').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column prop="user_name" label="用户" width="90" />
        <el-table-column prop="module" label="模块" width="80" />
        <el-table-column prop="method" label="方式" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="tagType(row.method)" effect="plain">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="操作" min-width="160" show-overflow-tooltip />
        <el-table-column prop="path" label="接口" min-width="180" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="120" />
        <el-table-column prop="cost_ms" label="耗时" width="70" align="right">
          <template #default="{ row }">{{ row.cost_ms }}ms</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status < 400 ? 'success' : 'danger'" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无日志" :image-size="60" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const loading = ref(false)
const kw = ref('')
const range = ref(null)

function tagType(m) {
  if (m === 'DELETE') return 'danger'
  if (m === 'PUT') return 'warning'
  if (m === 'ERR') return 'info'
  return 'success'
}

async function load() {
  loading.value = true
  try {
    const params = { kw: kw.value, size: 500 }
    if (range.value && range.value.length === 2) {
      params.start = range.value[0]
      params.end = range.value[1]
    }
    const res = await request.get('/logs', { params })
    items.value = res.data.items
  } catch (e) {
    // 日志查询失败静默
  } finally {
    loading.value = false
  }
}

function reset() {
  kw.value = ''
  range.value = null
  load()
}

onMounted(load)
</script>

<style scoped>
.log-tip { font-size: 11px; color: #c0c4cc; }
</style>
