<template>
  <el-container class="layout">
    <!-- 侧栏 -->
    <el-aside width="200px" class="aside" :class="{ 'mobile-open': mobileMenuOpen }">
      <div class="brand">📦 jxc进销存 <span class="ver">v2.0</span></div>
      <el-menu :default-active="activeMenu" router class="menu">
        <template v-for="item in menus" :key="item.path">
          <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
            <template #title>
              <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="c in item.children" :key="c.path" :index="c.path">
              <el-icon v-if="c.icon"><component :is="c.icon" /></el-icon>
              <span>{{ c.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
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
        <div class="header-right">
          <span class="user">
            <el-icon><UserFilled /></el-icon> {{ user.displayName || user.username }}
          </span>
          <el-button link type="danger" @click="logout">
            <el-icon><SwitchButton /></el-icon> 退出
          </el-button>
        </div>
      </el-header>

      <!-- 内容 -->
      <el-main class="main">
        <router-view />
      </el-main>

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
import { Menu, Camera, Box, QuestionFilled, DataAnalysis } from '@element-plus/icons-vue'

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
  display: flex;
  flex-direction: column;
  transition: transform .25s ease;
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
    width: 220px !important;
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
  border-right: none;
  background: transparent;
  --el-menu-text-color: rgba(255, 255, 255, .65);
  --el-menu-hover-bg-color: rgba(255, 255, 255, .06);
  --el-menu-active-color: #fff;
  --el-menu-bg-color: transparent;
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
  background: rgba(0, 0, 0, .2);
  min-width: 0;
}
.header {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
}
.hamburger {
  margin-right: 2px;
  color: #303133;
}
.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.user {
  font-size: 13px;
  color: #606266;
  margin-right: 12px;
}
.main {
  padding: 14px;
  overflow-y: auto;
}
/* 手机端底部导航 */
.bottom-nav {
  display: flex;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: #fff;
  border-top: 1px solid #e8e8e8;
  z-index: 1050;
  padding-bottom: env(safe-area-inset-bottom);
}
.bottom-nav .bn-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  text-decoration: none;
  font-size: 10px;
  gap: 2px;
  min-height: 44px;
}
.bottom-nav .bn-item.active {
  color: #1890ff;
}
.bottom-nav .bn-item:active {
  background: #f6f8fa;
}
@media (max-width: 767px) {
  .aside {
    display: flex;
  }
  .aside.mobile-open {
    display: flex;
  }
  .header-title {
    font-size: 13px;
  }
  .main {
    padding: 10px;
    padding-bottom: 66px;
  }
}
@media (min-width: 768px) {
  .bottom-nav {
    display: none;
  }
  .menu-overlay {
    display: none;
  }
}
</style>
