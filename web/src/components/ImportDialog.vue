<template>
  <el-dialog :model-value="modelValue" title="采购单导入" width="720px" top="6vh"
             @update:model-value="$emit('update:modelValue', $event)" :close-on-click-modal="false">
    <div class="import-tip">
      <p>一行一条明细；相同「采购单号」自动归并为一张单，单号留空则每行自成一张单。</p>
      <p>已存在的单号自动跳过（不会重复入库）。供应商 / 物料需已在基础资料中。</p>
    </div>
    <div class="import-bar">
      <el-button size="small" :loading="downloading" @click="downloadTemplate">
        <el-icon><Download /></el-icon> 下载导入模板
      </el-button>
      <el-upload :show-file-list="false" :auto-upload="false" :accept="'.xlsx,.xls'" :on-change="onFile">
        <el-button size="small" type="primary" :loading="uploading">
          <el-icon><Upload /></el-icon> 选择 Excel 上传
        </el-button>
      </el-upload>
    </div>

    <el-table v-if="errors.length" :data="errors" size="small" border max-height="260" class="mt">
      <el-table-column prop="row" label="行号" width="70" />
      <el-table-column prop="message" label="错误" show-overflow-tooltip />
    </el-table>
    <el-alert v-if="resultMsg" :title="resultMsg" :type="errors.length ? 'warning' : 'success'" class="mt" :closable="false" />
    <template #footer>
      <el-button size="small" @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { docApi } from '../api/doc'

const props = defineProps({
  modelValue: Boolean,
  docType: { type: String, default: 'purchase_order' },
})
const emit = defineEmits(['update:modelValue', 'reloaded'])

const downloading = ref(false)
const uploading = ref(false)
const errors = ref([])
const resultMsg = ref('')

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  URL.revokeObjectURL(url)
  a.remove()
}

async function downloadTemplate() {
  downloading.value = true
  try {
    const { blob, filename } = await docApi.importTemplate()
    saveBlob(blob, filename)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    downloading.value = false
  }
}

async function onFile(file) {
  const raw = file.raw
  if (!raw) return
  const formData = new FormData()
  formData.append('file', raw)
  errors.value = []
  resultMsg.value = ''
  uploading.value = true
  try {
    const res = await docApi.importPurchases(formData)
    resultMsg.value = `导入完成：建单 ${res.data.created} 张，跳过已存在 ${res.data.skipped} 行，失败 ${res.data.failCount} 行`
    ElMessage.success('导入完成')
    emit('reloaded')
  } catch (e) {
    const d = e.detail
    if (d && Array.isArray(d.errorRows) && d.errorRows.length) {
      errors.value = d.errorRows
      resultMsg.value = `部分成功：已建单 ${d.created}，跳过 ${d.skipped}，失败 ${d.errorRows.length} 行`
    } else {
      ElMessage.error(e.message)
    }
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.import-tip { font-size: 12px; color: #909399; background: #f5f7fa; border-radius: 4px; padding: 8px 12px; margin-bottom: 12px; }
.import-tip p { margin: 2px 0; }
.import-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.mt { margin-top: 12px; }
</style>
