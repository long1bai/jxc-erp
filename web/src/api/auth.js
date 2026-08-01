import request from '../utils/request'

/** 认证模块 */
export const authApi = {
  login: (data) => request.post('/auth/login', data),
  logout: () => request.post('/auth/logout'),
}
