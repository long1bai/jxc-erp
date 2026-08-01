<template>
  <el-card shadow="never">
    <template #header>
      <PageHeader title="AI 报价助手">
        <template #icon><ChatDotRound /></template>
      </PageHeader>
    </template>

    <el-alert type="info" :closable="false" class="mb">
      描述线材需求（线规/材质/长度/加工工艺），AI 自动给出材料费 + 加工费 + 利润的报价明细。
    </el-alert>

    <div class="chat-box" ref="chatBox">
      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <div class="bubble" v-html="renderMd(m.content)"></div>
      </div>
      <div v-if="loading" class="msg assistant">
        <div class="bubble loading">🤔 AI 正在计算报价…</div>
      </div>
    </div>

    <div class="input-bar">
      <el-input v-model="input" type="textarea" :rows="2" resize="none"
                placeholder="例如：特软硅胶线 10AWG 红黑各 500 米，裁线 30cm 打端子，报个单价"
                @keydown.enter.exact.prevent="send" />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const messages = ref([{ role: 'assistant', content: '你好，我是报价助手。请描述你的线材需求（线规、材质、长度、数量、加工工艺），我来帮你算单价。' }])
const input = ref('')
const loading = ref(false)
const chatBox = ref(null)

function renderMd(text) {
  return String(text || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/\n{2,}/g, '<br/><br/>')
    .replace(/\n/g, '<br/>')
}

async function send() {
  const msg = input.value.trim()
  if (!msg) return
  messages.value.push({ role: 'user', content: msg })
  input.value = ''
  loading.value = true
  await nextTick()
  chatBox.value.scrollTop = chatBox.value.scrollHeight
  try {
    const res = await request.post('/ai/chat', { message: msg })
    messages.value.push({ role: 'assistant', content: res.data.reply })
  } catch (e) {
    ElMessage.error(e.message)
    messages.value.push({ role: 'assistant', content: '⚠️ ' + e.message })
  } finally {
    loading.value = false
    await nextTick()
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}
</script>

<style scoped>

.chat-box { height: 430px; overflow-y: auto; background: #f7f8fa; border: 1px solid #e4e7ed; border-radius: 6px; padding: 12px; margin-bottom: 10px; }
.msg { margin-bottom: 10px; display: flex; }
.msg.user { justify-content: flex-end; }
.msg.assistant { justify-content: flex-start; }
.bubble { max-width: 85%; padding: 8px 12px; border-radius: 8px; font-size: 13px; line-height: 1.7; white-space: normal; }
.msg.user .bubble { background: #409eff; color: #fff; }
.msg.assistant .bubble { background: #fff; border: 1px solid #e4e7ed; color: #303133; }
.loading { color: #909399; }
.input-bar { display: flex; gap: 8px; align-items: flex-end; }
.input-bar .el-input { flex: 1; }
</style>
