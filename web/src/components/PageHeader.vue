<template>
  <div class="card-header">
    <span class="title">
      <el-icon v-if="$slots.icon"><slot name="icon" /></el-icon>
      <slot name="title">{{ title }}</slot>
    </span>
    <slot />
  </div>
</template>

<script setup>
/**
 * 页面卡片头：图标 + 标题 + 右侧操作区（按钮/hint）。
 * 用法：
 *   <PageHeader title="用户管理">
 *     <template #icon><User /></template>
 *     <el-button type="primary" size="small" @click="openCreate">新增用户</el-button>
 *   </PageHeader>
 * - 无图标可不传 #icon；无操作区则只有标题
 */
defineProps({
  title: { type: String, default: '' },
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-weight: 600; display: flex; align-items: center; gap: 6px; }
</style>
