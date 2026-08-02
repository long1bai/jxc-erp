<template>
  <el-container class="layout">
    <!-- 主菜单 -->
    <el-aside width="200px" class="aside" :class="{ 'mobile-open': mobileMenuOpen }">
      <div class="brand">📦 {{ systemName }} <span class="ver">v2.0</span></div>
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
          <div class="header-title">{{ systemName }}系统</div>
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
        <!-- 右侧实时统计面板（按当前模块显示相关指标） -->
        <el-aside v-if="!isMobile && statCards.length" width="212px" class="query-panel">
          <div class="qp-title">实时统计</div>
          <div v-for="c in statCards" :key="c.label" class="qp-stat" @click="c.path && router.push(c.path)">
            <div class="qp-stat-num">{{ c.value }}</div>
            <div class="qp-stat-label">
              <el-icon size="12"><component :is="c.icon" /></el-icon> {{ c.label }}
            </div>
          </div>
          <div class="qp-hint">数据每 60 秒刷新 · 点击数字跳转</div>
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
const systemName = ref('进销存系统')
function onResize() {
  isMobile.value = window.innerWidth <= 767
  if (!isMobile.value) mobileMenuOpen.value = false
}

// 菜单由后端按角色下发（动态菜单，前端不硬编码）；系统名由后端配置下发（买家可改）
onMounted(async () => {
  window.addEventListener('resize', onResize)
  try {
    const [mr, cr] = await Promise.all([
      request.get('/menus'),
      request.get('/config/company').catch(() => null),
    ])
    menus.value = mr.data.menus || []
    if (cr?.data?.systemName) systemName.value = cr.data.systemName
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

// ===== 右侧实时统计面板（按当前模块显示相关指标，数据复用 /api/dashboard/data）=====
const dashData = ref(null)
const statCards = computed(() => {
  const d = dashData.value?.data
  if (!d) return []
  const stats = d.stats || {}
  // 模块 → 指标卡片（value / label / icon / 跳转路径）
  const cards = {
    trade: [
      { value: stats.pending_orders ?? 0, label: '待出货订单', icon: 'Timer', path: '/orders' },
      { value: stats.partial_orders ?? 0, label: '部分出货', icon: 'Loading', path: '/orders' },
      { value: stats.full_unshipped ?? 0, label: '未发货总量', icon: 'Van', path: '/deliveries' },
    ],
    'stock-report': [
      { value: stats.stock_alerts ?? 0, label: '库存预警', icon: 'Warning', path: '/stock/inventory' },
      { value: stats.month_sales ?? 0, label: '本月销售额', icon: 'TrendCharts', path: '/sales-reports' },
      { value: stats.month_purchases ?? 0, label: '本月采购额', icon: 'ShoppingCart', path: '/purchase-reports' },
    ],
    finance: [
      { value: d.receivables?.length ?? 0, label: '待收款项', icon: 'Money', path: '/finance/receivables' },
      { value: d.payables?.length ?? 0, label: '待付款项', icon: 'Wallet', path: '/finance/payables' },
      { value: stats.month_receipts ?? 0, label: '本月收款', icon: 'TrendCharts', path: '/finance/account-reports' },
    ],
    production: [
      { value: stats.month_deliveries ?? 0, label: '本月送货', icon: 'Van', path: '/deliveries' },
      { value: stats.month_new_orders ?? 0, label: '本月新订单', icon: 'Document', path: '/orders' },
    ],
    work: [
      { value: stats.month_deliveries ?? 0, label: '本月送货量', icon: 'Van', path: '/deliveries' },
      { value: stats.stock_alerts ?? 0, label: '库存预警', icon: 'Warning', path: '/stock/inventory' },
    ],
  }
  return cards[selectedModule.value] || []
})
// 拉取统计（复用仪表盘缓存接口，60 秒内不重算）
async function loadDash() {
  try {
    const res = await request.get('/dashboard/data')
    dashData.value = res
  } catch { /* 忽略 */ }
}
watch(() => route.path, () => { if (statCards.value.length) loadDash() })
onMounted(loadDash)

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
.qp-stat {
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  cursor: pointer;
  transition: all .15s;
}
.qp-stat:hover {
  background: #ecf5ff;
  border-color: #b3d8ff;
}
.qp-stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
  line-height: 1.2;
}
.qp-stat-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #606266;
  margin-top: 2px;
}
.qp-hint {
  font-size: 10px;
  color: #c0c4cc;
  text-align: center;
  padding: 4px 0;
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
