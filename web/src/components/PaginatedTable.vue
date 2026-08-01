<template>
  <div>
    <el-table :data="rows" v-bind="$attrs" size="small" stripe>
      <el-table-column v-if="showIndex" type="index" label="序号" width="60" :index="index" />
      <slot />
      <template #empty>
        <el-empty :description="emptyText" :image-size="60" />
      </template>
    </el-table>
    <el-pagination v-if="items.length > pageSize" :current-page="page" :page-size="pageSize"
                   :page-sizes="pageSizes" :total="items.length"
                   layout="total, sizes, prev, pager, next, jumper" small
                   style="margin-top: 10px; justify-content: flex-end"
                   @current-change="page = $event"
                   @size-change="onSizeChange" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

/**
 * 通用前端分页表格：数据量小（几百条内）时全量加载、本地翻页，避免页面过长。
 * 用法：
 *   <PaginatedTable :items="employees" show-index empty-text="暂无员工">
 *     <el-table-column prop="name" label="姓名" />
 *   </PaginatedTable>
 * - 列定义用默认插槽传入；show-index 时自动加跨页连续的序号列
 * - items 引用变化（重新加载/搜索）时自动回到第 1 页
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  pageSize: { type: Number, default: 15 },
  emptyText: { type: String, default: '暂无数据' },
  showIndex: { type: Boolean, default: false },
  pageSizes: { type: Array, default: () => [10, 15, 20, 50] },
})

// 每页条数可切换（默认取 props.pageSize）
const pageSize = ref(props.pageSize)
const page = ref(1)
const rows = computed(() =>
  props.items.slice((page.value - 1) * pageSize.value, page.value * pageSize.value)
)

// 切换每页条数：回到第 1 页
function onSizeChange(s) {
  pageSize.value = s
  page.value = 1
}

// 重新加载/搜索（items 换新引用）时回到第 1 页；翻页只改 page 不影响
watch(() => props.items, () => { page.value = 1 })

/** 序号列 index 函数：跨页连续编号 */
function index(i) {
  return (page.value - 1) * pageSize.value + i + 1
}
</script>
