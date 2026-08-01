<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="报工管理">
        <template #icon><Timer /></template>
      </PageHeader>
    </template>

    <el-tabs v-model="tab">
      <!-- ===== 打卡报工（默认页，工人只有这一页） ===== -->
      <el-tab-pane :label="isAdmin ? '📝 报工' : '📝 报工'" name="punch">
        <div class="punch-wrap">
          <!-- 报工卡：开工前 = 可编辑表单；开工后 = 计时 + 完成数量 -->
          <div class="punch-card">
            <!-- 开工前 -->
            <template v-if="!active">
              <div class="p-field">
                <div class="p-label"><span class="req">*</span>姓名</div>
                <el-select v-model="form.employeeId" filterable placeholder="选择姓名" size="small"
                           style="width: 100%" @change="onEmpChange">
                  <el-option v-for="e in employees" :key="e.id"
                             :label="`${e.name}${e.group_name ? '（' + e.group_name + '）' : ''}`" :value="e.id" />
                </el-select>
              </div>
              <div class="p-field">
                <div class="p-label"><span class="req">*</span>工序</div>
                <el-select v-model="form.processId" filterable placeholder="选择工序" size="small"
                           style="width: 100%">
                  <el-option v-for="p in filteredProcesses" :key="p.id"
                             :label="p.name" :value="p.id" />
                </el-select>
              </div>
              <!-- 物料搜索：料号/名称自动匹配（选填） -->
              <div class="p-field">
                <div class="p-label">物料 <span class="p-optional">选填</span></div>
                <div class="mat-search">
                  <el-input v-model="matQuery" size="small" placeholder="料号/名称，如 HX50"
                            clearable @input="onMatInput" @clear="clearMat" />
                  <div v-if="matResults.length" class="mat-drop">
                    <div v-for="m in matResults" :key="m.id" class="mat-item" @click="pickMat(m)">
                      <b>{{ m.name }}</b>
                      <span class="mat-code">{{ m.code || '' }} {{ m.spec || '' }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="form.materialName" class="mat-chosen">✅ {{ form.materialName }}</div>
              </div>
              <div class="p-field">
                <div class="p-label">备注</div>
                <el-input v-model="form.remark" size="small" placeholder="选填" />
              </div>
              <div class="p-field">
                <div class="p-label">开始时间 <span class="p-optional">默认当前，可改</span></div>
              <div class="p-time-row">
                <el-time-picker v-model="form.startTime" value-format="HH:mm:ss" placeholder="现在"
                                style="flex: 1" size="small" />
                <el-button link type="primary" size="small" @click="setNow('startTime')">现在</el-button>
              </div>
              </div>
              <div class="p-field">
                <div class="p-label">拍照 <span class="p-optional">选填，最多3张</span></div>
                <div class="photo-row">
                  <div v-for="(p, i) in photoPreviews" :key="i" class="photo-thumb">
                    <img :src="p" alt="" />
                    <button type="button" class="photo-del" @click="removePhoto(i)">×</button>
                  </div>
                  <div v-if="photoFiles.length < 3" class="photo-add" @click="$refs.photoInput.click()">
                    <el-icon><Camera /></el-icon>
                  </div>
                  <input ref="photoInput" type="file" accept="image/*" capture="environment" multiple
                         style="display: none" @change="addPhotos" />
                </div>
              </div>
              <el-button type="primary" size="small" class="p-start" :loading="starting" @click="start">
                <el-icon><VideoPlay /></el-icon> 开始
              </el-button>
            </template>

            <!-- 开工后：只读摘要 + 计时 + 完成数量 -->
            <template v-else>
              <div class="active-info">
                <div class="ai-row">
                  <span class="ai-name">{{ active.employee_name }}</span>
                  <span class="ai-group">{{ active.group_name || '' }}</span>
                </div>
                <div class="ai-row">
                  <span class="ai-proc">{{ active.process_name }}</span>
                  <span class="ai-time">开始 {{ (active.start_time || '').slice(11, 16) }}</span>
                </div>
              </div>
              <div class="timer-box">
                <div class="timer">{{ elapsed }}</div>
                <small class="timer-sub">已用时间（自动扣除午休/晚餐）</small>
              </div>
              <div class="p-field">
                <div class="p-label"><span class="req">*</span>完成数量</div>
                <el-input-number v-model="finishQty" :min="1" :precision="2" :step="10"
                                 controls-position="right" style="width: 100%" size="small" />
              </div>
              <div class="p-field">
                <div class="p-label">结束时间 <span class="p-optional">默认当前，可改</span></div>
              <div class="p-time-row">
                <el-time-picker v-model="finishTime" value-format="HH:mm:ss" placeholder="现在"
                                style="flex: 1" size="small" />
                <el-button link type="primary" size="small" @click="setNow('finishTime')">现在</el-button>
              </div>
              </div>
              <div v-if="photoPreviews.length" class="active-photos">
                <img v-for="(p, i) in photoPreviews" :key="i" :src="p" alt="报工照片" class="active-photo" />
              </div>
              <div class="active-btns">
                <el-button type="success" size="small" class="p-finish" :loading="finishing" @click="finish">
                  <el-icon><CircleCheck /></el-icon> 结束
                </el-button>
                <a href="javascript:void(0)" class="p-cancel" @click="cancel">取消报工</a>
              </div>
            </template>
          </div>

          <!-- 今日已完成 -->
          <div class="punch-done">
            <div class="done-title">
              今日已完成
              <span v-if="myDone.length" class="done-sum">共 {{ myDone.length }} 次 · {{ myDoneTotal }} 件</span>
            </div>
            <el-table :data="myDone" size="small" stripe max-height="520">
              <el-table-column label="时间" width="90">
                <template #default="{ row }">{{ (row.end_time || '').slice(11, 16) }}</template>
              </el-table-column>
              <el-table-column prop="process_name" label="工序" min-width="100" show-overflow-tooltip />
              <el-table-column prop="quantity" label="数量" width="90" align="right"  sortable/>
              <el-table-column prop="duration" label="用时" width="110"  sortable/>
              <template #empty><el-empty description="今天还没报工" :image-size="50" /></template>
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== 报工记录（管理） ===== -->
      <el-tab-pane v-if="isAdmin" label="📋 报工记录" name="records">
        <div class="search-bar">
          <el-date-picker v-model="filterDate" type="date" value-format="YYYY-MM-DD" size="small"
                          placeholder="选择日期" style="width: 160px" @change="load(1)" />
          <el-select v-model="filterGroup" placeholder="分组" clearable size="small" style="width: 120px" @change="load(1)">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
          <el-input v-model="keyword" placeholder="搜索员工/工序" clearable size="small" style="width: 180px"
                    @keyup.enter="load(1)" @clear="load(1)" />
          <el-button type="primary" size="small" @click="load(1)">查询</el-button>
          <div class="flex-spacer"></div>
          <el-button type="primary" size="small" @click="openCreate">
            <el-icon><Plus /></el-icon> 补录报工
          </el-button>
        </div>
        <el-table v-if="!isMobile" :data="items" size="small" stripe v-loading="loading" max-height="460">
          <el-table-column prop="report_date" label="日期" width="100"  sortable/>
          <el-table-column prop="employee_name" label="员工" width="100" />
          <el-table-column prop="group_name" label="分组" width="80" />
          <el-table-column prop="process_name" label="工序" min-width="110" />
          <el-table-column prop="quantity" label="数量" width="90" align="right"  sortable/>
          <el-table-column prop="start_time" label="开始" width="150" />
          <el-table-column prop="end_time" label="结束" width="150" />
          <el-table-column prop="duration" label="时长" width="110"  sortable/>
          <el-table-column prop="wage" label="工资" width="90" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.wage) }}</template>
          </el-table-column>
          <el-table-column label="图片" width="80" align="center">
            <template #default="{ row }">
              <div v-if="Number(row.image_count) > 0" class="img-cell">
                <el-image :src="row.first_image" :preview-src-list="imgList(row)" preview-teleported
                          fit="cover" style="width: 36px; height: 36px; border-radius: 4px;">
                  <template #error><span class="no-img">无图</span></template>
                </el-image>
                <span v-if="Number(row.image_count) > 1" class="img-badge">{{ row.image_count }}</span>
              </div>
              <span v-else class="no-img">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无报工记录" :image-size="60" /></template>
        </el-table>
        <!-- 卡片（手机） -->
        <div v-else class="m-cards">
          <div v-for="row in items" :key="row.id" class="m-card">
            <div class="m-card-head">
              <span class="m-name">{{ row.employee_name }} · {{ row.process_name }}</span>
              <span style="display:flex;align-items:center;gap:6px">
                <img v-if="Number(row.image_count) > 0 && row.first_image" :src="row.first_image" class="m-thumb" alt=""
                     @error="e => e.target.style.display = 'none'" />
                <el-tag size="small" type="info">{{ (row.start_time || '').slice(11, 16) }}–{{ (row.end_time || '').slice(11, 16) }}</el-tag>
              </span>
            </div>
            <div class="m-card-body">
              <div class="m-row"><span>日期</span><b>{{ row.report_date }}</b></div>
              <div class="m-row"><span>分组</span><b>{{ row.group_name || '—' }}</b></div>
              <div class="m-row"><span>数量</span><b>{{ row.quantity }}</b></div>
              <div class="m-row"><span>时长</span><b>{{ row.duration }}</b></div>
              <div class="m-row"><span>工资</span><b>￥{{ fmt(row.wage) }}</b></div>
              <div class="m-row" v-if="row.remark"><span>备注</span><b>{{ row.remark }}</b></div>
            </div>
            <div class="m-actions">
              <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
            </div>
          </div>
          <div v-if="!items.length" class="m-empty">暂无报工记录</div>
        </div>
        <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                       :page-size="size" :current-page="page" @current-change="load" />
      </el-tab-pane>

      <!-- ===== 统计汇总（日报/月报合并，粒度切换） ===== -->
      <el-tab-pane v-if="isAdmin" label="📊 统计汇总" name="stats">
        <div class="search-bar">
          <el-radio-group v-model="statMode" size="small" @change="loadStats">
            <el-radio-button value="daily">日报</el-radio-button>
            <el-radio-button value="monthly">月报</el-radio-button>
          </el-radio-group>
          <el-date-picker v-if="statMode === 'daily'" v-model="dailyDate" type="date" value-format="YYYY-MM-DD" size="small"
                          style="width: 160px" @change="loadDaily" />
          <el-date-picker v-else v-model="monthVal" type="month" value-format="YYYY-MM" size="small"
                          style="width: 140px" @change="loadMonthly" />
          <el-button type="primary" size="small" @click="loadStats">查询</el-button>
        </div>
        <el-table v-if="statMode === 'daily'" :data="daily" size="small" stripe max-height="460">
          <el-table-column prop="group_name" label="分组" width="100" />
          <el-table-column prop="employee_name" label="员工" width="110" />
          <el-table-column prop="report_count" label="报工次数" width="90" align="right"  sortable/>
          <el-table-column label="图片" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="Number(row.image_count) > 0" type="info" size="small">📷 {{ row.image_count }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="total_quantity" label="总数量" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total_quantity) }}</template>
          </el-table-column>
          <el-table-column prop="total_wage" label="工资" align="right" sortable>
            <template #default="{ row }"><b>{{ fmt(row.total_wage) }}</b></template>
          </el-table-column>
          <template #empty><el-empty description="当日无报工" :image-size="60" /></template>
        </el-table>
        <el-table v-else :data="monthly" size="small" stripe max-height="460">
          <el-table-column prop="employee_name" label="员工" width="110" />
          <el-table-column prop="group_name" label="分组" width="100" />
          <el-table-column prop="work_days" label="出勤天数" width="90" align="right"  sortable/>
          <el-table-column label="图片" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="Number(row.image_count) > 0" type="info" size="small">📷 {{ row.image_count }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="total_quantity" label="总数量" align="right" sortable>
            <template #default="{ row }">{{ fmt(row.total_quantity) }}</template>
          </el-table-column>
          <el-table-column prop="total_wage" label="工资" align="right" sortable>
            <template #default="{ row }"><b>{{ fmt(row.total_wage) }}</b></template>
          </el-table-column>
          <template #empty><el-empty description="当月无报工" :image-size="60" /></template>
        </el-table>
        <div class="sum-bar" v-if="statMode === 'daily'">合计：{{ fmt(dailySum) }}</div>
        <div class="sum-bar" v-else>合计：{{ fmt(monthlySum) }}</div>
      </el-tab-pane>
    </el-tabs>

    <!-- 补录报工弹窗（仅管理员用） -->
    <el-dialog v-model="createVisible" title="补录报工" width="720px">
      <el-form ref="createFormRef" :rules="createRules" label-width="80px" size="small">
        <el-form-item label="员工" prop="employeeId" required>
          <el-select v-model="form.employeeId" filterable placeholder="选择员工" style="width: 100%">
            <el-option v-for="e in employees" :key="e.id" :label="`${e.name}（${e.group_name || '未分组'}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工序" prop="processId" required>
          <el-select v-model="form.processId" filterable placeholder="选择工序" style="width: 100%">
            <el-option v-for="p in processes" :key="p.id" :label="`${p.name}（${p.group_name || '未分组'}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="8">
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="数量" prop="quantity" required>
              <el-input-number v-model="form.quantity" :min="1" :precision="2" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="日期">
              <el-date-picker v-model="form.reportDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="8">
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="开始">
              <div class="p-time-row">
                <el-time-picker v-model="form.startTime" value-format="HH:mm:ss" placeholder="现在"
                                style="flex: 1" size="small" />
                <el-button link type="primary" size="small" @click="setNow('startTime')">现在</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="结束">
              <div class="p-time-row">
                <el-time-picker v-model="form.endTime" value-format="HH:mm:ss" placeholder="现在"
                                style="flex: 1" size="small" />
                <el-button link type="primary" size="small" @click="setNow('endTime')">现在</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
        <el-form-item label="拍照">
          <div class="photo-row">
            <div v-for="(p, i) in createPreviews" :key="i" class="photo-thumb">
              <img :src="p" alt="" />
              <button type="button" class="photo-del" @click="removeCreatePhoto(i)">×</button>
            </div>
            <div v-if="createPhotos.length < 3" class="photo-add" @click="$refs.createPhotoInput.click()">
              <el-icon><Camera /></el-icon>
            </div>
            <input ref="createPhotoInput" type="file" accept="image/*" capture="environment" multiple
                   style="display: none" @change="addCreatePhotos" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const route = useRoute()
const isAdmin = (() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}').role === 'admin' } catch { return false }
})()

// 初始 tab：管理员可直达 记录/日报/月报；工人永远落在打卡页
const tab = ref('punch')
const routeTab = new URLSearchParams(window.location.hash.split('?')[1] || '').get('tab')
if (isAdmin && (routeTab === 'records' || routeTab === 'daily' || routeTab === 'monthly')) {
  tab.value = routeTab
}
watch(() => route.query.tab, (v) => {
  if (isAdmin && (v === 'records' || v === 'daily' || v === 'monthly')) tab.value = v
})

// ==================== 打卡报工 ====================
const employees = ref([])
const processes = ref([])
const groups = ref([])
const form = reactive({ employeeId: null, processId: null, quantity: 1, reportDate: today(), startTime: '', endTime: '', remark: '', materialId: null, materialName: '' })
const active = ref(null)          // 进行中的报工记录
const finishQty = ref(1)
const finishTime = ref('') // 结束时间（空=用当前时间，finish 兜底）
const elapsed = ref('00:00')
const starting = ref(false)
const finishing = ref(false)
const myDone = ref([])

const statMode = ref('daily')
function loadStats() {
  if (statMode.value === 'daily') loadDaily()
  else loadMonthly()
}

// 补录弹窗校验
const createFormRef = ref(null)
const createRules = {
  employeeId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  processId: [{ required: true, message: '请选择工序', trigger: 'change' }],
  quantity: [{ required: true, message: '请填写数量', trigger: 'change' }],
}

// 时间选择器：直接绑定提交变量；"现在"按钮一键填入当前时间
function setNow(key) {
  if (key === 'finishTime') finishTime.value = nowTime()
  else form[key] = nowTime()
}

const myDoneTotal = computed(() =>
  myDone.value.reduce((s, r) => s + (Number(r.quantity) || 0), 0)
)
const matQuery = ref('')
const matResults = ref([])
const photoFiles = ref([])        // 待上传的报工照片（File[]）
const photoPreviews = ref([])     // 本地预览（dataURL）
const createPhotos = ref([])      // 补录弹窗的照片
const createPreviews = ref([])
let timerInterval = null
let matTimer = null

// 员工选中的分组 → 过滤工序
const curEmp = computed(() => employees.value.find(e => e.id === form.employeeId) || null)
const filteredProcesses = computed(() => {
  if (!curEmp.value || !curEmp.value.group_id) return processes.value
  const gid = Number(curEmp.value.group_id)
  const gs = processes.value.filter(p => p.group_id == null || Number(p.group_id) === 0 || Number(p.group_id) === gid)
  return gs.length ? gs : processes.value
})

function onEmpChange() {
  if (!form.employeeId) return
  localStorage.setItem('wr_last_emp', String(form.employeeId))
  form.processId = null
  loadLastProcess()
  loadInProgress()
  loadMyDone()
}

async function loadLastProcess() {
  if (!form.employeeId) return
  try {
    const res = await request.get('/work/reports/last-process', { params: { employeeId: form.employeeId } })
    if (res.data.processId) form.processId = Number(res.data.processId)
  } catch { /* 忽略 */ }
}

async function loadInProgress() {
  if (!form.employeeId) return
  try {
    const res = await request.get('/work/reports/in-progress', { params: { employeeId: form.employeeId } })
    const items = res.data.items || []
    active.value = items.length ? items[0] : null
    if (active.value) {
      finishQty.value = 1
      startTimer()
    } else {
      stopTimer()
    }
  } catch { /* 忽略 */ }
}

async function loadMyDone() {
  if (!form.employeeId) return
  try {
    const res = await request.get('/work/reports/my', { params: { employeeId: form.employeeId, date: today() } })
    myDone.value = res.data.items || []
  } catch { /* 忽略 */ }
}

async function start() {
  if (!form.employeeId) { ElMessage.warning('请选择姓名'); return }
  if (!form.processId) { ElMessage.warning('请选择工序'); return }
  form.startTime = form.startTime || nowTime() // 用户确认过开始时间则保留；未确认则用当前时刻
  starting.value = true
  try {
    const fd = new FormData()
    fd.append('employeeId', form.employeeId)
    fd.append('processId', form.processId)
    if (form.remark) fd.append('remark', form.remark)
    if (form.materialId) fd.append('materialId', form.materialId)
    if (form.materialName) fd.append('materialName', form.materialName)
    // 开始时间：填了就用填的（补录场景），否则后端用当前时间
    if (form.startTime) fd.append('startTime', `${today()} ${form.startTime}`)
    photoFiles.value.forEach(f => fd.append('images', f))
    const res = await request.post('/work/reports/start', fd)
    active.value = {
      id: res.data.id,
      employee_name: curEmp.value ? curEmp.value.name : '',
      group_name: curEmp.value ? (curEmp.value.group_name || '') : '',
      process_name: (processes.value.find(p => p.id === form.processId) || {}).name || '',
      start_time: form.startTime ? `${today()} ${form.startTime}` : nowStr(),
    }
    finishQty.value = 1
    // 结束时间不预填：空=结束时刻用当前时间（finish 兜底 nowStr）；用户改过才用改的
    finishTime.value = ''
    // 清空表单，方便下一次
    form.processId = null
    form.remark = ''
    form.materialId = null
    form.materialName = ''
    form.startTime = ''
    matQuery.value = ''
    photoFiles.value = []
    photoPreviews.value = []
    startTimer()
    ElMessage.success('已开始，完工时填数量点结束')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    starting.value = false
  }
}

async function finish() {
  if (!active.value) return
  if (!finishQty.value || finishQty.value <= 0) { ElMessage.warning('请填写完成数量'); return }
  finishing.value = true
  try {
    const res = await request.post(`/work/reports/${active.value.id}/finish`, {
      quantity: finishQty.value,
      endTime: finishTime.value ? `${today()} ${finishTime.value}` : nowStr(),
    })
    ElMessage.success(`✅ 完成 ${finishQty.value} 个`)
    active.value = null
    stopTimer()
    elapsed.value = '00:00'
    photoPreviews.value = []
    loadMyDone()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    finishing.value = false
  }
}

// ==================== 拍照 ====================
function addPhotos(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  const room = 3 - photoFiles.value.length
  for (const f of files.slice(0, room)) {
    photoFiles.value.push(f)
    const reader = new FileReader()
    reader.onload = (ev) => photoPreviews.value.push(ev.target.result)
    reader.readAsDataURL(f)
  }
  if (files.length > room) ElMessage.info('最多上传 3 张照片')
}
function removePhoto(i) {
  photoFiles.value.splice(i, 1)
  photoPreviews.value.splice(i, 1)
}

async function cancel() {
  if (!active.value) return
  try {
    await ElMessageBox.confirm('确定取消这次报工？', '取消确认', { type: 'warning' })
  } catch { return }
  try {
    await request.post(`/work/reports/${active.value.id}/cancel`)
    ElMessage.success('已取消')
    active.value = null
    stopTimer()
    elapsed.value = '00:00'
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// 计时：扣除午休 12:00-13:00、晚餐 17:30-18:00
function startTimer() {
  stopTimer()
  tick()
  timerInterval = setInterval(tick, 1000)
}
function stopTimer() {
  if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
}
function tick() {
  if (!active.value) return
  const st = new Date((active.value.start_time || nowStr()).replace(' ', 'T'))
  const secs = workSeconds(st, new Date())
  const h = Math.floor(secs / 3600), m = Math.floor((secs % 3600) / 60), s = secs % 60
  elapsed.value = h > 0
    ? `${h}时${String(m).padStart(2, '0')}分${String(s).padStart(2, '0')}秒`
    : `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}
function workSeconds(st, now) {
  let totalSec = Math.floor((now - st) / 1000)
  if (totalSec <= 0) return 0
  const breaks = [[12 * 60, 13 * 60], [17 * 60 + 30, 18 * 60]]
  let startMin = st.getHours() * 60 + st.getMinutes()
  let endMin = now.getHours() * 60 + now.getMinutes()
  let sub = 0
  for (const b of breaks) {
    const oStart = Math.max(startMin, b[0])
    const oEnd = Math.min(endMin, b[1])
    if (oEnd > oStart) sub += (oEnd - oStart) * 60
  }
  return Math.max(0, totalSec - sub)
}

// ==================== 物料搜索 ====================
function onMatInput() {
  clearTimeout(matTimer)
  const q = matQuery.value.trim()
  if (q.length < 1) { matResults.value = []; return }
  matTimer = setTimeout(async () => {
    try {
      const res = await request.get('/materials', { params: { keyword: q, size: 8 } })
      matResults.value = (res.data.items || []).slice(0, 8)
    } catch { matResults.value = [] }
  }, 300)
}
function pickMat(m) {
  form.materialId = m.id
  form.materialName = m.name
  matQuery.value = m.name
  matResults.value = []
}
function clearMat() {
  form.materialId = null
  form.materialName = ''
  matResults.value = []
}

// ==================== 记录管理（管理员） ====================
const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const isMobile = ref(window.innerWidth <= 767)
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 767 })
const saving = ref(false)
const filterDate = ref(today())
const filterGroup = ref(null)
const createVisible = ref(false)
const daily = ref([])
const dailyDate = ref(today())
const monthly = ref([])
const monthVal = ref(today().slice(0, 7))

const dailySum = computed(() => daily.value.reduce((s, r) => s + Number(r.total_wage || 0), 0))
const monthlySum = computed(() => monthly.value.reduce((s, r) => s + Number(r.total_wage || 0), 0))

function fmt(v) {
  return Number(v || 0).toLocaleString()
}

// 多图预览：image_paths 逗号分隔 → 数组（含历史/缺失路径也不影响预览）
function imgList(row) {
  if (row.image_paths) {
    const arr = String(row.image_paths).split(',').filter(Boolean)
    if (arr.length) return arr
  }
  return row.first_image ? [row.first_image] : []
}

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, keyword: keyword.value }
    if (filterDate.value) params.date = filterDate.value
    if (filterGroup.value) params.groupId = filterGroup.value
    const res = await request.get('/work/reports', { params })
    items.value = res.data.items
    total.value = Number(res.data.total)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [g, e, p] = await Promise.all([
      request.get('/work/groups'),
      request.get('/work/employees'),
      request.get('/work/processes'),
    ])
    groups.value = g.data.items
    employees.value = e.data.items
    processes.value = p.data.items
  } catch { /* 忽略 */ }
}

async function loadDaily() {
  const res = await request.get('/work/stats/daily', { params: { date: dailyDate.value } })
  daily.value = res.data.items
}
async function loadMonthly() {
  const res = await request.get('/work/stats/monthly', { params: { month: monthVal.value } })
  monthly.value = res.data.items
}

function openCreate() {
  Object.assign(form, { employeeId: null, processId: null, quantity: 1, reportDate: today(), startTime: '', endTime: '', remark: '', materialId: null, materialName: '' })
  createPhotos.value = []
  createPreviews.value = []
  createVisible.value = true
}

async function save() {
  const ok = await createFormRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const fd = new FormData()
    fd.append('employeeId', form.employeeId)
    fd.append('processId', form.processId)
    fd.append('quantity', form.quantity)
    if (form.startTime) fd.append('startTime', `${form.reportDate} ${form.startTime}`)
    if (form.endTime) fd.append('endTime', `${form.reportDate} ${form.endTime}`)
    fd.append('reportDate', form.reportDate)
    if (form.remark) fd.append('remark', form.remark)
    if (form.materialId) fd.append('materialId', form.materialId)
    if (form.materialName) fd.append('materialName', form.materialName)
    createPhotos.value.forEach(f => fd.append('images', f))
    await request.post('/work/reports', fd)
    ElMessage.success('补录成功')
    createVisible.value = false
    createPhotos.value = []
    createPreviews.value = []
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

// 补录拍照
function addCreatePhotos(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  const room = 3 - createPhotos.value.length
  for (const f of files.slice(0, room)) {
    createPhotos.value.push(f)
    const reader = new FileReader()
    reader.onload = (ev) => createPreviews.value.push(ev.target.result)
    reader.readAsDataURL(f)
  }
  if (files.length > room) ElMessage.info('最多上传 3 张照片')
}
function removeCreatePhoto(i) {
  createPhotos.value.splice(i, 1)
  createPreviews.value.splice(i, 1)
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除 ${row.employee_name} 的报工记录？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await request.delete(`/work/reports/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function today() {
  return new Date().toISOString().slice(0, 10)
}
function nowTime() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
function nowStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onMounted(async () => {
  await loadOptions()
  if (isAdmin) { load(1); loadDaily(); loadMonthly() }
  // 优先：账号关联的报工员工（工人登录自动带出，不用选姓名）
  let empId = null
  try {
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    if (u.workEmployeeId) empId = Number(u.workEmployeeId)
  } catch { /* 忽略 */ }
  if (empId && employees.value.some(e => e.id === empId)) {
    form.employeeId = empId
    onEmpChange()
    return
  }
  // 其次：恢复上次选择的员工
  const last = localStorage.getItem('wr_last_emp')
  if (last && employees.value.some(e => e.id === Number(last))) {
    form.employeeId = Number(last)
    onEmpChange()
  }
})
onUnmounted(stopTimer)
</script>

<style scoped>

.flex-spacer { flex: 1; }


.no-img { color: #c0c4cc; }
.img-cell { position: relative; display: inline-block; }
.img-badge {
  position: absolute; top: -4px; right: -4px; min-width: 14px; height: 14px; line-height: 14px;
  padding: 0 3px; border-radius: 7px; background: #f56c6c; color: #fff;
  font-size: 10px; text-align: center;
}

/* ===== 打卡报工 ===== */
.punch-wrap { display: flex; gap: 12px; align-items: flex-start; flex-wrap: wrap; }
.punch-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px;
  width: 380px;
  flex-shrink: 0;
}
.punch-done { flex: 1; min-width: 300px; }
.done-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; color: #303133; }
.done-sum { font-size: 12px; font-weight: 400; color: #909399; margin-left: 8px; }
.p-field { margin-bottom: 10px; }
.p-time-row { display: flex; gap: 6px; align-items: center; }
.p-time-row :deep(.el-date-editor) { min-width: 0; }
.p-label { font-size: 12px; color: #606266; margin-bottom: 4px; }
.req { color: #f56c6c; margin-right: 2px; }
.p-optional { color: #c0c4cc; font-size: 11px; }
.p-start { width: 100%; height: 40px; font-size: 15px; margin-top: 4px; }
.active-info {
  background: #f7f8fa;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.ai-row { display: flex; justify-content: space-between; margin-bottom: 4px; }
.ai-row:last-child { margin-bottom: 0; }
.ai-name { font-weight: 600; font-size: 14px; }
.ai-group { font-size: 12px; color: #909399; }
.ai-proc { font-weight: 600; }
.ai-time { font-size: 12px; color: #909399; }
.timer-box {
  background: rgba(230, 162, 60, 0.1);
  border-radius: 6px;
  padding: 10px;
  text-align: center;
  margin-bottom: 10px;
}
.timer { font-size: 26px; font-weight: bold; color: #e6a23c; font-variant-numeric: tabular-nums; }
.timer-sub { font-size: 11px; color: #909399; }
.active-btns { display: flex; align-items: center; gap: 12px; }
.p-finish { height: 40px; font-size: 15px; flex: 1; }
.p-cancel { font-size: 12px; color: #f56c6c; flex-shrink: 0; }
.mat-search { position: relative; }
.mat-drop {
  position: absolute;
  top: 100%;
  left: 0; right: 0;
  z-index: 100;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  max-height: 220px;
  overflow-y: auto;
  box-shadow: 0 2px 12px rgba(0, 0, 0, .1);
}
.mat-item { padding: 7px 10px; cursor: pointer; font-size: 12px; display: flex; justify-content: space-between; gap: 8px; }
.mat-item:hover { background: #f5f7fa; }
.mat-code { color: #909399; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mat-chosen { font-size: 12px; color: #67c23a; margin-top: 4px; }

/* 拍照 */
.photo-row { display: flex; gap: 6px; flex-wrap: wrap; }
.photo-thumb { position: relative; width: 56px; height: 56px; border-radius: 4px; overflow: hidden; border: 1px solid #e4e7ed; }
.photo-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.photo-del {
  position: absolute; top: 0; right: 0; width: 16px; height: 16px; line-height: 14px;
  background: rgba(0, 0, 0, .55); color: #fff; border: none; border-radius: 0 0 0 4px;
  cursor: pointer; font-size: 11px; padding: 0;
}
.photo-add {
  width: 56px; height: 56px; border: 2px dashed #ccc; border-radius: 4px;
  display: flex; align-items: center; justify-content: center; cursor: pointer;
  color: #aaa; font-size: 20px;
}
.photo-add:hover { border-color: #409eff; color: #409eff; }
.active-photos { display: flex; gap: 6px; margin-bottom: 10px; flex-wrap: wrap; }
.active-photo { width: 56px; height: 56px; object-fit: cover; border-radius: 4px; border: 1px solid #e4e7ed; }

@media (max-width: 767px) {
  .punch-card { width: 100%; }
  .punch-done { min-width: 100%; }
}

/* 手机卡片 */






.m-row span { color: #909399; }
.m-row b { color: #303133; font-weight: 500; }


.m-thumb { width: 36px; height: 36px; border-radius: 4px; object-fit: cover; border: 1px solid #ebeef5; }
</style>
