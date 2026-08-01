<template>
  <el-container class="layout">
    <!-- 主菜单 -->
    <el-aside width="200px" class="aside" :class="{ 'mobile-open': mobileMenuOpen }">
      <div class="brand">📦 jxc进销存 <span class="ver">v2.0</span></div>
      <div v-if="currentMod" class="mod-title">
        <el-icon v-if="currentMod?.icon" size="14"><component :is="currentMod.icon" /></el-icon>
        {{ currentMod?.title }}
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <template v-for="item in menus" :key="item.path">
              <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
                <template #title>
                  <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
                  <span>{{ item.title }}</span>
                </template>
                <el-menu-item v-for="c in item.children" :key="c.path" :index="c.path" @click="go(c.path)">
                  <el-icon v-if="c.icon"><component :is="c.icon" /></el-icon>
                  <span>{{ c.title }}</span>
                </el-menu-item>
              </el-sub-menu>
              <el-menu-item v-else :index="item.path" @click="go(item.path)">
                <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </el-menu-item>
            </template>
          </el-menu>
    </el-aside>

    <!-- 手机端侧栏遮罩 -->
    <div v-if="mobileMenuOpen" class="menu-overlay" @click="mobileMenuOpen = false"></div>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-button v-if="isMobile" link class="hamburger" @click="mobileMenuOpen = true" aria-label="菜单">
            <el-icon size="20"><Menu /></el-icon>
          </el-button>
          <div class="header-title">jxc进销存系统</div>
        </div>
        <!-- 顶部快捷功能条（象过河功能导航条） -->
        <div class="quick-bar" v-if="!isMobile && quickItems.length">
          <div v-for="q in quickItems" :key="q.path" class="quick-item" @click="router.push(q.path)">
            <el-icon size="15"><component :is="q.icon" /></el-icon>
            <span>{{ q.title }}</span>
          </div>
        </div>
        <div class="header-right">
          <span class="user">
            <el-icon><UserFilled /></el-icon> {{ user.displayName || user.username }}
          </span>
          <el-button link type="danger" @click="logout">
            <el-icon><SwitchButton /></el-icon> 退出
          </el-button>
        </div>
      </el-header>

      <div class="main-flex">
        <!-- 内容 -->
        <el-main class="main">
          <router-view />
        </el-main>
        <!-- 右侧查询面板（象过河相关查询统计） -->
        <el-aside v-if="!isMobile && reportGroups.length" width="212px" class="query-panel">
          <div class="qp-title">相关查询统计</div>
          <div v-for="g in reportGroups" :key="g.title" class="qp-group">
            <div class="qp-group-title">{{ g.title }}</div>
            <div v-for="it in g.items" :key="it.path" class="qp-item" @click="router.push(it.path)">
              <el-icon size="14"><component :is="it.icon || 'Document'" /></el-icon>
              <span>{{ it.title }}</span>
            </div>
          </div>
          <div v-if="!reportGroups.length" class="qp-empty">选中左侧模块查看相关功能</div>
        </el-aside>
      </div>

      <!-- 手机端底部导航 -->
      <nav class="bottom-nav" v-if="isMobile">
        <router-link to="/work/reports" class="bn-item" :class="{ active: isActive('/work/reports') }">
          <el-icon :size="20"><Timer /></el-icon><span>报工</span>
        </router-link>
        <router-link to="/ai/photo" class="bn-item" :class="{ active: isActive('/ai/photo') }">
          <el-icon :size="20"><Camera /></el-icon><span>拍照</span>
        </router-link>
        <router-link to="/stock/inventory" class="bn-item" :class="{ active: isActive('/stock/inventory') }">
          <el-icon :size="20"><Box /></el-icon><span>库存</span>
        </router-link>
        <router-link to="/help" class="bn-item" :class="{ active: isActive('/help') }">
          <el-icon :size="20"><QuestionFilled /></el-icon><span>指南</span>
        </router-link>
        <a href="javascript:void(0)" class="bn-item" @click="mobileMenuOpen = true">
          <el-icon :size="20"><Menu /></el-icon><span>菜单</span>
        </a>
      </nav>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import request from '../utils/request'
import { Menu, Camera, Box, QuestionFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 高亮：收付款单页面按 ?tab= 区分菜单项
const activeMenu = computed(() => {
  if (route.path === '/finance/vouchers') {
    return route.query.tab === 'payments'
      ? '/finance/vouchers?tab=payments'
      : '/finance/vouchers?tab=receipts'
  }
  return route.path
})

const userStore = useUserStore()
const user = computed(() => userStore.user)
const role = computed(() => user.value.role || 'employee')
const menus = ref([])

const isMobile = ref(window.innerWidth <= 767)
const mobileMenuOpen = ref(false)
function onResize() {
  isMobile.value = window.innerWidth <= 767
  if (!isMobile.value) mobileMenuOpen.value = false
}

// 菜单由后端按角色下发（动态菜单，前端不硬编码）
onMounted(async () => {
  window.addEventListener('resize', onResize)
  try {
    const mr = await request.get('/menus')
    menus.value = mr.data.menus || []
    syncModule() // 菜单就绪后同步当前模块选中
  } catch (e) {
    // 菜单加载失败不阻塞页面（路由守卫仍按角色拦截）
  }
})
onUnmounted(() => window.removeEventListener('resize', onResize))

// 切页后自动收起手机抽屉
watch(() => route.path, () => { mobileMenuOpen.value = false })

function isActive(path) {
  return route.path.startsWith(path)
}

// 显式跳转（不依赖 el-menu router 模式，保证点击一定响应）
function go(path) {
  if (route.path === path) return
  router.push(path)
}

// ===== 模块关联（右侧查询面板 + 菜单标题：自动跟随当前页面所在模块）=====
const modGroups = computed(() => menus.value.filter((m) => (m.children || []).length))
const singles = computed(() => menus.value.filter((m) => !(m.children || []).length))
const selectedModule = ref('')
const currentMod = computed(() => modGroups.value.find((g) => g.path === selectedModule.value))
// 路由变化 → 自动关联所属模块（用于右面板和菜单标题；无分组页如仪表盘/拍照显示全量）
function syncModule() {
  const clean = route.path.split('?')[0]
  for (const g of modGroups.value) {
    const hit = (g.children || []).some((c) => clean.startsWith(c.path.split('?')[0]))
    if (hit) { selectedModule.value = g.path; return }
  }
  selectedModule.value = ''
}
watch(() => route.path, syncModule, { immediate: true })

// ===== 顶部快捷功能条（常用功能一键直达，按角色自动过滤）=====
const QUICK_PATHS = ['/orders', '/purchases', '/stock/inventory',
  '/finance/vouchers?tab=receipts', '/finance/vouchers?tab=payments', '/work/reports']
const quickItems = computed(() => {
  const all = []
  const walk = (items) => { for (const m of items) { all.push(m); if (m.children) walk(m.children) } }
  walk(menus.value)
  return QUICK_PATHS.map((p) => all.find((m) => m.path === p)).filter(Boolean)
})

// ===== 右侧查询面板（当前模块的 功能操作 + 查询统计）=====
const reportGroups = computed(() => {
  if (!selectedModule.value) return []
  const g = modGroups.value.find((x) => x.path === selectedModule.value)
  if (!g) return []
  const kids = g.children || []
  const query = kids.filter((k) => /报表|统计|查询|分析|盘点|流水|对账/.test(k.title))
  const other = kids.filter((k) => !/报表|统计|查询|分析|盘点|流水|对账/.test(k.title))
  const groups = []
  if (other.length) groups.push({ title: '功能操作', items: other })
  if (query.length) groups.push({ title: '查询统计', items: query })
  return groups
})

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
  } catch {
    return
  }
  userStore.clear()
  ElMessage.success('已退出')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: #001529;
  transition: transform .25s ease;
}
.brand {
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  padding: 16px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, .1);
  white-space: nowrap;
}
.brand .ver {
  font-size: 10px;
  color: rgba(255, 255, 255, .45);
  font-weight: normal;
}
.menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;
  background: transparent;
  --el-menu-text-color: rgba(255, 255, 255, .65);
  --el-menu-hover-bg-color: rgba(255, 255, 255, .06);
  --el-menu-active-color: #fff;
  --el-menu-bg-color: transparent;
}
.mod-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, .85);
  padding: 10px 14px 4px;
  font-weight: 600;
}
.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  color: rgba(255, 255, 255, .65);
}
.menu :deep(.el-menu-item.is-active) {
  background: rgba(24, 144, 255, .15);
  border-right: 3px solid #1890ff;
}
.menu :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
}
/* 手机端：侧栏变抽屉 */
@media (max-width: 767px) {
  .aside {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 1060;
    transform: translateX(-100%);
    width: 240px !important;
  }
  .aside.mobile-open {
    transform: translateX(0);
  }
}
.menu-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, .35);
  z-index: 1055;
}
.header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  height: 52px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.header-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}
/* 顶部快捷功能条 */
.quick-bar {
  flex: 1;
  display: flex;
  gap: 6px;
  align-items: center;
  overflow-x: auto;
  padding: 0 8px;
}
.quick-item {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 14px;
  padding: 3px 10px;
  cursor: pointer;
  white-space: nowrap;
  transition: all .15s;
}
.quick-item:hover {
  color: #fff;
  background: #409eff;
  border-color: #409eff;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.user {
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 4px;
}
/* 主内容 + 右侧查询面板 */
.main-flex {
  flex: 1;
  display: flex;
  min-height: 0;
}
.main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: #f5f7fa;
  padding: 12px;
}
.query-panel {
  background: #fff;
  border-left: 1px solid #ebeef5;
  padding: 12px 10px;
  overflow-y: auto;
}
.qp-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.qp-group { margin-bottom: 10px; }
.qp-group-title {
  font-size: 11px;
  color: #909399;
  margin-bottom: 4px;
  padding-left: 2px;
}
.qp-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #409eff;
  padding: 5px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all .15s;
}
.qp-item:hover {
  background: #ecf5ff;
}
.qp-empty {
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
  padding: 20px 0;
}
/* 手机底部导航 */
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: space-around;
  padding: 6px 0 8px;
  z-index: 1000;
}
.bn-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 10px;
  color: #909399;
  text-decoration: none;
}
.bn-item.active { color: #409eff; }
@media (max-width: 767px) {
  .main { padding: 8px; padding-bottom: 64px; }
}
</style>
