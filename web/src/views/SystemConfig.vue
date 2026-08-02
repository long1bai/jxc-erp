<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="系统配置">
        <template #icon><Setting /></template>
      </PageHeader>
    </template>

    <el-tabs v-model="tab">
      <!-- ============ 公司信息 ============ -->
      <el-tab-pane label="🏢 公司信息" name="company">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                  title="修改后立即生效：登录页/侧边栏标题用「系统名称」，打印单抬头用「公司名称」" />
        <el-form :model="companyForm" label-width="110px" style="max-width: 560px">
          <el-form-item label="系统名称">
            <el-input v-model="companyForm.system_name" placeholder="如：jxc进销存" />
          </el-form-item>
          <el-form-item label="公司名称">
            <el-input v-model="companyForm.company_name" placeholder="打印抬头/帮助页显示" />
          </el-form-item>
          <el-form-item label="公司地址">
            <el-input v-model="companyForm.company_address" />
          </el-form-item>
          <el-form-item label="公司电话">
            <el-input v-model="companyForm.company_phone" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveCompany">保存公司信息</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ 业务参数 ============ -->
      <el-tab-pane label="⚙️ 业务参数" name="biz">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                  title="单号前缀/休息时段等业务规则，改完保存立即生效（新单据按新规则生成）" />
        <el-form :model="bizForm" label-width="160px" style="max-width: 620px">
          <el-divider content-position="left">单号前缀</el-divider>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="采购入库单">
                <el-input v-model="bizForm.seq_cgdd" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="采购订单">
                <el-input v-model="bizForm.seq_po" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="采购退货单">
                <el-input v-model="bizForm.seq_th" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="收款单">
                <el-input v-model="bizForm.seq_rcv" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="付款单">
                <el-input v-model="bizForm.seq_pay" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="发票">
                <el-input v-model="bizForm.seq_inv" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="收支单">
                <el-input v-model="bizForm.seq_ie" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="转账单">
                <el-input v-model="bizForm.seq_zz" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">其他</el-divider>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="员工工号前缀">
                <el-input v-model="bizForm.employee_no_prefix" placeholder="如 W" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="物料默认分类">
                <el-input v-model="bizForm.default_material_category" placeholder="如 原材料" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="默认仓库ID">
                <el-input v-model="bizForm.default_warehouse" placeholder="如 1" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">报工休息时段（自动扣除）</el-divider>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="午休开始">
                <el-time-select v-model="bizForm.break_lunch_start" start="00:00" end="23:30" step="00:30" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="午休结束">
                <el-time-select v-model="bizForm.break_lunch_end" start="00:00" end="23:30" step="00:30" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="晚餐开始">
                <el-time-select v-model="bizForm.break_dinner_start" start="00:00" end="23:30" step="00:30" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="晚餐结束">
                <el-time-select v-model="bizForm.break_dinner_end" start="00:00" end="23:30" step="00:30" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveBiz">保存业务参数</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ AI 配置 ============ -->
      <el-tab-pane label="🤖 AI 配置" name="ai">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                  title="对接任意 OpenAI 兼容供应商：改地址/密钥/模型/提示词即可，不用改代码。密钥只显示后4位" />
        <el-form :model="aiForm" label-width="130px" style="max-width: 640px">
          <el-form-item label="API 地址">
            <el-input v-model="aiForm.api_url" placeholder="https://api.xxx.com/v1/chat/completions" />
          </el-form-item>
          <el-form-item label="API 密钥">
            <el-input v-model="aiForm.api_key" placeholder="留空保持不变" show-password>
              <template #prepend v-if="aiForm.api_key_set">已设置</template>
            </el-input>
          </el-form-item>
          <el-form-item label="对话模型">
            <el-input v-model="aiForm.chat_model" placeholder="如 qwen-plus" />
          </el-form-item>
          <el-form-item label="视觉模型">
            <el-input v-model="aiForm.vision_model" placeholder="如 qwen3-vl-plus" />
          </el-form-item>
          <el-form-item label="报价提示词">
            <el-input v-model="aiForm.system_prompt" type="textarea" :rows="6"
                      placeholder="支持 {company_name} 占位符" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveAi">保存 AI 配置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import request from '../utils/request'
import PageHeader from '../components/PageHeader.vue'

const tab = ref('company')
const saving = ref(false)

// ============ 公司信息 ============
const companyForm = ref({
  system_name: '', company_name: '', company_address: '', company_phone: '',
})

// ============ 业务参数 ============
const bizForm = ref({
  seq_cgdd: '', seq_po: '', seq_th: '', seq_rcv: '', seq_pay: '',
  seq_inv: '', seq_ie: '', seq_zz: '',
  employee_no_prefix: '', default_material_category: '', default_warehouse: '',
  break_lunch_start: '', break_lunch_end: '', break_dinner_start: '', break_dinner_end: '',
})

// ============ AI 配置 ============
const aiForm = ref({
  api_url: '', api_key: '', api_key_set: false, chat_model: '', vision_model: '', system_prompt: '',
})

// ============ 加载 ============
async function loadAll() {
  try {
    const [cfgRes, aiRes] = await Promise.all([
      request.get('/config/all').catch(() => null),
      request.get('/config/ai').catch(() => null),
    ])
    // 系统参数 → 表单
    const items = cfgRes?.data?.items || []
    const map = {}
    for (const it of items) map[it.config_key] = it.config_value
    companyForm.value = {
      system_name: map.system_name || '',
      company_name: map.company_name || '',
      company_address: map.company_address || '',
      company_phone: map.company_phone || '',
    }
    const bizKeys = ['seq_cgdd','seq_po','seq_th','seq_rcv','seq_pay','seq_inv','seq_ie','seq_zz',
                     'employee_no_prefix','default_material_category','default_warehouse',
                     'break_lunch_start','break_lunch_end','break_dinner_start','break_dinner_end']
    for (const k of bizKeys) bizForm.value[k] = map[k] || ''
    // AI 配置
    if (aiRes?.data) {
      aiForm.value = {
        api_url: aiRes.data.api_url || '',
        api_key: aiRes.data.api_key || '',
        api_key_set: !!aiRes.data.api_key_set,
        chat_model: aiRes.data.chat_model || '',
        vision_model: aiRes.data.vision_model || '',
        system_prompt: aiRes.data.system_prompt || '',
      }
    }
  } catch (e) {
    ElMessage.error('加载配置失败：' + (e.message || e))
  }
}

// ============ 保存 ============
async function saveCompany() {
  saving.value = true
  try {
    const body = {}
    for (const [k, v] of Object.entries(companyForm.value)) body[k] = v
    await request.put('/config', body)
    ElMessage.success('公司信息已保存')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function saveBiz() {
  saving.value = true
  try {
    const body = {}
    for (const [k, v] of Object.entries(bizForm.value)) body[k] = v
    await request.put('/config', body)
    ElMessage.success('业务参数已保存')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function saveAi() {
  saving.value = true
  try {
    await request.put('/config/ai', {
      api_url: aiForm.value.api_url,
      api_key: aiForm.value.api_key,
      chat_model: aiForm.value.chat_model,
      vision_model: aiForm.value.vision_model,
      system_prompt: aiForm.value.system_prompt,
    })
    ElMessage.success('AI 配置已保存')
    // 刷新（重新脱敏显示）
    const aiRes = await request.get('/config/ai').catch(() => null)
    if (aiRes?.data) {
      aiForm.value.api_key = aiRes.data.api_key || ''
      aiForm.value.api_key_set = !!aiRes.data.api_key_set
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

onMounted(loadAll)
</script>
