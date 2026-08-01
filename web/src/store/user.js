import { defineStore } from 'pinia'

/**
 * 用户会话 Store（企业规范）：
 * 登录态统一管理，页面通过 useUserStore() 读取
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || '{}'),
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    displayName: (s) => s.user.displayName || s.user.username || '',
  },
  actions: {
    setSession(token, user) {
      this.token = token
      this.user = user || {}
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    clear() {
      this.token = ''
      this.user = {}
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
  },
})
