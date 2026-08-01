<template>
  <el-card shadow="never">
    <el-result icon="info" title="模块建设中" :sub-title="'「' + title + '」功能将在后续迭代中搬入'">
      <template #extra>
        <el-button type="primary" @click="$router.push('/dashboard')">返回仪表盘</el-button>
      </template>
    </el-result>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title || '该模块')
</script>
