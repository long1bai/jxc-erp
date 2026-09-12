<template>
  <el-dialog :model-value="modelValue" title="字段设置 / 模板识别" width="780px" top="6vh"
             @update:model-value="$emit('update:modelValue', $event)" :close-on-click-modal="false">
    <!-- 识别方式切换（默认传统解析，可切 AI） -->
    <div class="mode-bar">
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="excel">传统解析（默认）</el-radio-button>
        <el-radio-button value="ai">AI 识别</el-radio-button>
      </el-radio-group>
      <el-radio-group v-if="mode === 'ai'" v-model="aiInput" size="small" class="ai-input">
        <el-radio-button value="excel">Excel</el-radio-button>
        <el-radio-button value="photo">拍照</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 上传识别 -->
    <div class="upload-row">
      <el-upload :show-file-list="false" :auto-upload="false" :accept="accept" :on-change="onFile">
        <el-button size="small" type="primary" plain :loading="recognizing">
          上传 {{ mode === 'ai' && aiInput === 'photo' ? '照片' : 'Excel' }} 识别
        </el-button>
      </el-upload>
      <span class="tip">{{ tip }}</span>
    </div>

    <!-- 字段预览编辑表 -->
    <div class="preview-head">
      <span>字段列表（{{ preview.length }}）</span>
      <el-button size="small" link type="primary" @click="addManual">
        <el-icon><Plus /></el-icon> 手动添加字段
      </el-button>
    </div>
    <el-table :data="preview" size="small" border max-height="320">
      <el-table-column label="字段名（列头）" min-width="140">
        <template #default="{ row }"><el-input v-model="row.fieldName" placeholder="如 交期" /></template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-select v-model="row.fieldType" size="small">
            <el-option label="文本" value="text" />
            <el-option label="数字" value="number" />
            <el-option label="日期" value="date" />
            <el-option label="选项" value="select" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="选项（逗号分隔）" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.optionsText" :disabled="row.fieldType !== 'select'" placeholder="仅选项类型需要" />
        </template>
      </el-table-column>
      <el-table-column label="键" width="130">
        <template #default="{ row }"><el-input v-model="row.fieldKey" placeholder="自动生成" /></template>
      </el-table-column>
      <el-table-column width="46">
        <template #default="{ $index }">
          <el-button link type="danger" @click="preview.splice($index, 1)"><el-icon><Delete /></el-icon></el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无字段，上传识别或手动添加" :image-size="50" /></template>
    </el-table>

    <template #footer>
      <el-button size="small" @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" size="small" :loading="saving" @click="save">保存字段定义</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { docApi } from '../api/doc'

const props = defineProps({
  modelValue: Boolean,
  docType: { type: String, default: 'purchase_order' },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const mode = ref('excel')       // excel=传统解析(默认) | ai=AI识别
const aiInput = ref('excel')    // AI 内：excel | photo
const preview = ref([])
const recognizing = ref(false)
const saving = ref(false)

const accept = computed(() =>
  mode.value === 'ai' && aiInput.value === 'photo' ? 'image/*' : '.xlsx,.xls')

const tip = computed(() => {
  if (mode.value === 'excel') return '上传 Excel，后端自动解析表头并推断字段类型（日期/数字/选项/文本）'
  if (aiInput.value === 'photo') return '上传单据照片，AI 识别自定义列（qwen3-vl 多模态）'
  return '上传 Excel，AI 识别自定义列（qwen-plus）'
})

// 打开时：加载现有字段定义到预览（字段设置模式）
watch(() => props.modelValue, async (v) => {
  if (!v) return
  mode.value = 'excel'
  aiInput.value = 'excel'
  try {
    const res = await docApi.fields(props.docType)
    preview.value = (res.data || []).map((f) => ({
      fieldKey: f.fieldKey || '',
      fieldName: f.fieldName || '',
      fieldType: f.fieldType || 'text',
      optionsText: Array.isArray(f.options) ? f.options.join(',') : '',
    }))
  } catch {
    preview.value = []
  }
})

async function onFile(file) {
  const raw = file.raw
  if (!raw) return
  recognizing.value = true
  try {
    const formData = new FormData()
    formData.append('file', raw)
    formData.append('docType', props.docType)
    if (mode.value === 'ai') formData.append('inputMode', aiInput.value)
    const res = mode.value === 'ai'
      ? await docApi.recognizeAi(formData)
      : await docApi.recognizeExcel(formData)
    const fields = res.data.fields || []
    if (!fields.length) ElMessage.warning('未识别到自定义字段（可能都是标准列）')
    // 识别结果覆盖当前预览，便于确认后再保存
    preview.value = fields.map((f) => ({
      fieldKey: f.fieldKey || '',
      fieldName: f.fieldName || '',
      fieldType: f.fieldType || 'text',
      optionsText: Array.isArray(f.options) ? f.options.join(',') : '',
    }))
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    recognizing.value = false
  }
}

function addManual() {
  preview.value.push({ fieldKey: '', fieldName: '', fieldType: 'text', optionsText: '' })
}

async function save() {
  const fields = preview.value
    .map((f, i) => {
      const key = (f.fieldKey || '').trim()
      const name = (f.fieldName || '').trim()
      if (!key || !name) return null
      const options = f.fieldType === 'select'
        ? String(f.optionsText || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
        : []
      return { fieldKey: key, fieldName: name, fieldType: f.fieldType || 'text', options, sortOrder: i }
    })
    .filter(Boolean)
  if (!fields.length) {
    ElMessage.warning('请至少添加一个有效字段（需填写字段名和键名）')
    return
  }
  saving.value = true
  try {
    await docApi.saveFields({ docType: props.docType, fields })
    ElMessage.success('字段定义已保存')
    emit('saved')
    emit('update:modelValue', false)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.mode-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.ai-input { margin-left: 8px; }
.upload-row { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.tip { font-size: 12px; color: #909399; }
.preview-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; font-size: 13px; color: #606266; }
</style>
