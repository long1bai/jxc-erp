<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="always">
      <div class="login-brand">
        📦 jxc进销存
        <span class="ver">v2.0</span>
      </div>
      <div class="login-sub">Java + MySQL 新架构</div>
      <el-form label-position="top" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            clearable
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
            @keyup.enter="doLogin"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="doLogin"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '../api/auth'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()
const username = ref('admin')
const password = ref('')
const loading = ref(false)

// 浏览器密码管理器会在页面挂载后自动填充密码框（DOM 覆盖 v-model）——
// 延迟清空兜底，保证登录页始终从空密码开始
onMounted(() => {
  setTimeout(() => {
    password.value = ''
  }, 100)
})

async function doLogin() {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await authApi.login({
      username: username.value,
      password: password.value,
    })
    if (!res || !res.data || !res.data.token) {
      ElMessage.error((res && res.error) || '登录失败，请重试')
      return
    }
    userStore.setSession(res.data.token, res.data.user)
    ElMessage.success('登录成功')
    password.value = ''
    router.push('/')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #001529 0%, #003a70 100%);
  padding: 16px;
}
.login-card {
  width: 100%;
  max-width: 380px;
  border-radius: 10px;
  padding: 10px 6px;
}
.login-brand {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 2px;
}
.login-brand .ver {
  font-size: 11px;
  color: #909399;
  font-weight: normal;
  margin-left: 4px;
}
.login-sub {
  text-align: center;
  font-size: 12px;
  color: #a8abb2;
  margin-bottom: 18px;
}
.login-btn {
  width: 100%;
  margin-top: 4px;
}
</style>
