<template>
  <div class="filter-row">
    <el-date-picker
      v-model="range"
      type="daterange"
      value-format="YYYY-MM-DD"
      size="small"
      range-separator="~"
      start-placeholder="开始"
      end-placeholder="结束"
      style="width: 250px"
      :clearable="false"
    />
    <el-button type="primary" size="small" @click="$emit('query')">
      <el-icon><Search /></el-icon> 查询
    </el-button>
    <slot />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'query'])

const range = ref(props.modelValue)
watch(() => props.modelValue, (v) => { range.value = v })
watch(range, (v) => { emit('update:modelValue', v) })
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
  align-items: center;
}
</style>
