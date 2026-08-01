<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="拍照入库">
        <template #icon><Camera /></template>
        <span v-if="recognized" class="step-hint">第 2 步：核对识别结果，确认后自动入库</span>
        <span v-else class="step-hint">第 1 步：拍供应商送货单</span>
      </PageHeader>
    </template>

    <!-- ===== 第 1 步：拍照/选图 → 识别 ===== -->
    <template v-if="!recognized">
      <el-upload drag :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onFile">
        <el-icon class="up-icon"><Plus /></el-icon>
        <div class="el-upload__text">拍照或选择送货单图片 <em>（放平、光线足、横拍效果更好）</em></div>
      </el-upload>
      <div v-if="preview" class="preview">
        <img :src="preview" alt="预览" />
        <div class="preview-btns">
          <el-button size="small" @click="resetUpload">重新选择</el-button>
          <el-button type="primary" size="small" :loading="recognizing" :disabled="!preview" @click="recognize">
            {{ recognizing ? '识别中…' : '🔍 开始识别' }}
          </el-button>
        </div>
      </div>
      <div v-if="recognizing" class="recognizing">
        <el-icon class="is-loading" size="20"><Loading /></el-icon> 千问 VL 正在识别送货单，约 3-5 秒…
      </div>
    </template>

    <!-- ===== 第 2 步：核对 → 确认入库 ===== -->
    <template v-else>
      <el-form ref="formRef" :rules="formRules" label-width="70px" size="small">
        <div class="recon-grid">
          <el-form-item label="供应商" required prop="supplierId">
            <el-select v-model="form.supplierId" filterable remote :remote-method="searchSup"
                       :loading="supLoading" placeholder="输入名称搜索选择" style="width: 100%">
              <el-option v-for="s in supOptions" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
            <div v-if="form.supplierName && !form.supplierId" class="field-warn">
              ⚠️ 识别出「{{ form.supplierName }}」但库里没有，请在上方搜索选择，或去 基础资料-供应商 新增
            </div>
            <div v-else-if="form.supplierId" class="field-ok">✅ {{ form.supplierName }}</div>
          </el-form-item>
          <el-form-item label="入库日期">
            <el-date-picker v-model="form.poDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="选填" />
          </el-form-item>
        </div>
      </el-form>

      <div class="items-head">
        <span class="items-title">入库明细 — 可直接修改</span>
        <el-button type="success" size="small" @click="addRow"><el-icon><Plus /></el-icon> 添加行</el-button>
      </div>
      <el-table :data="rows" size="small" border max-height="420">
        <el-table-column label="物料" min-width="200">
          <template #default="{ row }">
            <el-select v-model="row.materialId" filterable remote :remote-method="q => searchMat(q, row)"
                       :loading="matLoading" placeholder="搜索选择或留空自动新建" style="width: 100%" clearable
                       @change="onMatChange(row)">
              <el-option v-for="m in matOptions" :key="m.id"
                         :label="`${m.name}${m.spec ? ' ' + m.spec : ''}${m.code ? '（' + m.code + '）' : ''}`"
                         :value="m.id" />
            </el-select>
            <div v-if="!row.materialId && row.name" class="new-mat-hint">🆕 将自动新建：{{ row.name }}</div>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" placeholder="名称" />
          </template>
        </el-table-column>
        <el-table-column label="规格" min-width="130">
          <template #default="{ row }"><el-input v-model="row.spec" size="small" placeholder="规格" /></template>
        </el-table-column>
        <el-table-column label="单位" width="80">
          <template #default="{ row }"><el-input v-model="row.unit" size="small" placeholder="条" /></template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :precision="2" :step="1" size="small"
                             controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="0.1" size="small"
                             controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="" width="50" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="rows.splice($index, 1)"><el-icon><Delete /></el-icon></el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无明细" :image-size="50" /></template>
      </el-table>
      <div class="sum-bar">
        共 {{ rows.length }} 行 ｜ 总数量 <b>{{ fmt(totalQty) }}</b> ｜ 总金额 <b>¥{{ fmt(totalAmt) }}</b>
      </div>

      <div class="confirm-btns">
        <el-button size="small" @click="resetAll">↺ 重新拍照</el-button>
        <el-button type="primary" size="small" :loading="confirming" @click="confirm">
          <el-icon><CircleCheck /></el-icon> ✅ 确认入库
        </el-button>
      </div>
    </template>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Loading, CircleCheck } from '@element-plus/icons-vue'
import request from '../utils/request'

const preview = ref(false)

const formRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
}
('')
const imageBase64 = ref('')
const recognizing = ref(false)
const recognized = ref(false)
const confirming = ref(false)
const supLoading = ref(false)
const matLoading = ref(false)
const supOptions = ref([])
const matOptions = ref([])
let supTimer = null
let matTimer = null

const form = reactive({ supplierId: null, supplierName: '', poDate: today(), remark: '' })
const rows = ref([])

const totalQty = computed(() => rows.value.reduce((s, r) => s + Number(r.quantity || 0), 0))
const totalAmt = computed(() => rows.value.reduce((s, r) => s + Number(r.quantity || 0) * Number(r.unitPrice || 0), 0))

function fmt(v) {
  return Number(v || 0).toLocaleString(undefined, { maximumFractionDigits: 2 })
}

// ============ 第 1 步：上传 + 识别 ============
function onFile(file) {
  const reader = new FileReader()
  reader.onload = (e) => {
    preview.value = e.target.result
    imageBase64.value = e.target.result.split(',')[1]
  }
  reader.readAsDataURL(file.raw)
}

function resetUpload() {
  preview.value = ''
  imageBase64.value = ''
}

async function recognize() {
  if (!imageBase64.value) return
  recognizing.value = true
  try {
    const res = await request.post('/photo/recognize', { imageBase64: imageBase64.value })
    const d = res.data
    // 供应商
    form.supplierId = d.supplierId || null
    form.supplierName = d.supplierName || ''
    if (d.docDate && /^\d{4}-\d{2}-\d{2}$/.test(d.docDate)) form.poDate = d.docDate
    // 明细
    const raw = Array.isArray(d.items) ? d.items : []
    rows.value = raw.map((it) => ({
      materialId: null,
      name: it.name || '',
      spec: it.spec || '',
      unit: it.unit || '',
      quantity: Number(it.quantity || 0),
      unitPrice: Number(it.unit_price || 0),
    }))
    if (!rows.value.length) rows.value = [emptyRow()]
    // 识别出的物料名自动匹配现有物料
    await autoMatchMaterials()
    recognized.value = true
    ElMessage.success(`识别到 ${rows.value.length} 行明细，请核对后确认入库`)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    recognizing.value = false
  }
}

async function autoMatchMaterials() {
  const jobs = rows.value.map(async (row) => {
    if (!row.name) return
    try {
      const res = await request.get('/materials', { params: { keyword: row.name.trim().slice(0, 12), size: 5 } })
      const items = res.data.items || []
      // 优先名称完全相等，其次包含
      const hit = items.find(m => m.name === row.name)
        || items.find(m => m.name && row.name && (m.name.includes(row.name) || row.name.includes(m.name)))
      if (hit) {
        row.materialId = hit.id
        if (!row.spec) row.spec = hit.spec || ''
        if (!row.unit) row.unit = hit.unit || ''
      }
    } catch { /* 匹配失败保留原样，确认时自动新建 */ }
  })
  await Promise.all(jobs)
}

// ============ 第 2 步：供应商/物料远程搜索 ============
function searchSup(q) {
  clearTimeout(supTimer)
  supTimer = setTimeout(async () => {
    supLoading.value = true
    try {
      const res = await request.get('/suppliers', { params: { keyword: (q || '').trim(), size: 20 } })
      supOptions.value = res.data.items || []
    } catch { supOptions.value = [] } finally { supLoading.value = false }
  }, 300)
}

function searchMat(q) {
  clearTimeout(matTimer)
  matTimer = setTimeout(async () => {
    matLoading.value = true
    try {
      const res = await request.get('/materials', { params: { keyword: (q || '').trim(), size: 20 } })
      matOptions.value = res.data.items || []
    } catch { matOptions.value = [] } finally { matLoading.value = false }
  }, 300)
}

function onMatChange(row) {
  const m = matOptions.value.find(x => x.id === row.materialId)
  if (m) {
    row.name = m.name
    if (!row.spec) row.spec = m.spec || ''
    if (!row.unit) row.unit = m.unit || ''
  }
}

function emptyRow() {
  return { materialId: null, name: '', spec: '', unit: '', quantity: 1, unitPrice: 0 }
}
function addRow() {
  rows.value.push(emptyRow())
}

// ============ 确认入库 ============
async function confirm() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!form.supplierId) { ElMessage.warning('请选择供应商'); return }
  const valid = rows.value.filter(r => Number(r.quantity || 0) > 0)
  if (!valid.length) { ElMessage.warning('请至少填一条数量大于 0 的明细'); return }
  confirming.value = true
  try {
    const res = await request.post('/photo/confirm', {
      supplierId: form.supplierId,
      poDate: form.poDate,
      remark: form.remark,
      items: valid.map(r => ({
        materialId: r.materialId || null,
        name: r.name || '',
        spec: r.spec || '',
        unit: r.unit || '',
        quantity: r.quantity,
        unitPrice: r.unitPrice || 0,
      })),
    })
    ElMessage.success(`✅ 入库成功！采购单 ${res.data.poNo}，共 ${res.data.created} 项物料入库`)
    resetAll()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    confirming.value = false
  }
}

function resetAll() {
  recognized.value = false
  resetUpload()
  form.supplierId = null
  form.supplierName = ''
  form.poDate = today()
  form.remark = ''
  rows.value = []
}

function today() {
  return new Date().toISOString().slice(0, 10)
}
</script>

<style scoped>
.step-hint { font-size: 12px; color: #909399; }
.up-icon { font-size: 32px; color: #c0c4cc; }
.preview { margin-top: 12px; border: 1px solid #e4e7ed; border-radius: 6px; padding: 8px; }
.preview img { width: 100%; max-height: 380px; object-fit: contain; display: block; }
.preview-btns { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.recognizing { display: flex; align-items: center; justify-content: center; gap: 8px; color: #409eff; font-size: 14px; padding: 16px 0; }
.recon-grid { max-width: 520px; }
.field-warn { font-size: 12px; color: #e6a23c; line-height: 1.4; margin-top: 2px; }
.field-ok { font-size: 12px; color: #67c23a; margin-top: 2px; }
.items-head { display: flex; justify-content: space-between; align-items: center; margin: 6px 0 8px; }
.items-title { font-size: 13px; font-weight: 600; }
.new-mat-hint { font-size: 11px; color: #67c23a; margin-top: 2px; }

.confirm-btns { display: flex; justify-content: flex-end; gap: 10px; margin-top: 12px; }
.confirm-btns .el-button:last-child { height: 40px; font-size: 15px; padding: 0 24px; }
</style>
