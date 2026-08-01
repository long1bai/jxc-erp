import axios from 'axios'

/**
 * 统一请求封装（企业规范）：
 * - 请求带 token
 * - 业务失败（success=false）统一 reject → 页面 catch 显示 error
 * - 401 自动清理会话并跳登录
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    if (res.data && res.data.success === false) {
      return Promise.reject(new Error(res.data.error || '操作失败'))
    }
    return res.data
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!window.location.hash.includes('/login')) {
        window.location.hash = '#/login'
      }
    }
    const msg =
      (err.response && err.response.data && err.response.data.error) ||
      err.message ||
      '网络错误'
    return Promise.reject(new Error(msg))
  }
)

export default request
