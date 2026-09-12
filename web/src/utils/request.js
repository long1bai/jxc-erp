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
      const err = new Error(res.data.error || '操作失败')
      err.detail = res.data.data || null
      return Promise.reject(err)
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

/**
 * 文件下载 helper：responseType=blob。
 * - 业务失败（后端返回 JSON）→ reject(Error)，e.detail 为后端 data
 * - 成功 → resolve({ blob, filename })，filename 从 Content-Disposition 解析
 */
request.download = (url, params) =>
  axios.get('/api' + url, {
    params,
    responseType: 'blob',
    headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
  }).then((resp) => {
    const blob = resp.data
    const ct = resp.headers['content-type'] || ''
    if (ct.includes('application/json')) {
      return blob.text().then((text) => {
        let msg = '下载失败'
        let detail = null
        try {
          const d = JSON.parse(text)
          msg = d.error || msg
          detail = d.data || null
        } catch { /* 非 JSON 文本 */ }
        const err = new Error(msg)
        err.detail = detail
        throw err
      })
    }
    // 解析文件名（filename*=UTF-8''xxx.xlsx）
    let filename = 'download.xlsx'
    const cd = resp.headers['content-disposition'] || ''
    const m = cd.match(/filename\*=UTF-8''([^;]+)/)
    if (m) filename = decodeURIComponent(m[1])
    return { blob, filename }
  })

export default request
