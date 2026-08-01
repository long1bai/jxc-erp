<template>
  <div class="mermaid-box" v-html="svg"></div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import mermaid from 'mermaid'

let initialized = false
const svg = ref('')

const props = defineProps({
  id: { type: String, required: true },
  code: { type: String, required: true },
})

async function render() {
  if (!initialized) {
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'loose',
      theme: 'base',
      themeVariables: {
        primaryColor: '#e6f7ff',
        primaryBorderColor: '#1890ff',
        primaryTextColor: '#096dd9',
        lineColor: '#91caff',
        fontSize: '13px',
        fontFamily: 'Microsoft YaHei, PingFang SC, sans-serif',
      },
      flowchart: { curve: 'basis', htmlLabels: true, padding: 8 },
    })
    initialized = true
  }
  try {
    // mermaid.render 的 id 必须全局唯一且不重复，用时间戳规避
    const { svg: s } = await mermaid.render(`${props.id}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`, props.code)
    svg.value = s
  } catch (e) {
    svg.value = `<div style="color:#f56c6c;font-size:12px;padding:8px;">流程图渲染失败：${(e && e.message) || e}</div>`
  }
}

onMounted(render)
watch(() => props.code, render)
</script>

<style scoped>
.mermaid-box {
  background: #fafbfc;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 8px;
  text-align: center;
  overflow-x: auto;
}
.mermaid-box :deep(svg) {
  max-width: 100%;
  height: auto;
}
</style>
