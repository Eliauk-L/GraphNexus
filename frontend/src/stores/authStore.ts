import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginParams, type LoginResult, type RoleVO } from '@/api/auth'
import { useRouter } from 'vue-router'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('accessToken'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const userInfo = ref<LoginResult['userInfo'] | null>(null)
  const roles = ref<RoleVO[]>([])

  const isAuthenticated = computed(() => !!accessToken.value)
  const userName = computed(() => userInfo.value?.realName ?? userInfo.value?.username ?? '')
  const userInitial = computed(() => userName.value.charAt(0).toUpperCase())

  function hasRole(role: string): boolean {
    return userInfo.value?.roles?.includes(role) ?? false
  }

  async function login(params: LoginParams) {
    const res = await authApi.login(params)
    const d = res.data.data
    accessToken.value = d.accessToken
    refreshToken.value = d.refreshToken
    userInfo.value = d.userInfo
    localStorage.setItem('accessToken', d.accessToken)
    localStorage.setItem('refreshToken', d.refreshToken)
    return d
  }

  async function refresh() {
    if (!refreshToken.value) throw new Error('No refresh token')
    const res = await authApi.refresh(refreshToken.value)
    const d = res.data.data
    accessToken.value = d.accessToken
    refreshToken.value = d.refreshToken
    localStorage.setItem('accessToken', d.accessToken)
    localStorage.setItem('refreshToken', d.refreshToken)
    if (d.userInfo) userInfo.value = d.userInfo
    return d
  }

  async function logout() {
    if (refreshToken.value) {
      try { await authApi.logout(refreshToken.value) } catch {}
    }
    accessToken.value = null
    refreshToken.value = null
    userInfo.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
  }

  function restoreSession() {
    const at = localStorage.getItem('accessToken')
    const rt = localStorage.getItem('refreshToken')
    if (at) accessToken.value = at
    if (rt) refreshToken.value = rt
  }

  return { accessToken, refreshToken, userInfo, roles, isAuthenticated, userName, userInitial, hasRole, login, refresh, logout, restoreSession }
})
