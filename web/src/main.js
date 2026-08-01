import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注册全部图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')

// ===== 前端错误日志：全局捕获 JS 错误 / 未处理 Promise，上报后端（操作日志页可见）=====
function reportFrontendError(message) {
  try {
    navigator.sendBeacon('/api/logs/client-error',
      new Blob([JSON.stringify({ message, url: location.href })], { type: 'application/json' }))
  } catch { /* 忽略 */ }
}
window.addEventListener('error', (e) => reportFrontendError(String(e.message || e.error || '脚本错误')))
window.addEventListener('unhandledrejection', (e) => reportFrontendError(String(e.reason || '未处理的 Promise 异常')))
