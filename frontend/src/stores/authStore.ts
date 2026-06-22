import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginParams, type LoginResult } from '@/api/auth'
import { useRouter } from 'vue-router'

const USER_KEY = 'userInfo'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('accessToken'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const userInfo = ref<LoginResult['userInfo'] | null>(loadUserInfo())

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
    saveUserInfo(d.userInfo)
    saveUserInfo(d.userInfo)
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
    if (d.userInfo) {
      userInfo.value = d.userInfo
      saveUserInfo(d.userInfo)
    }
    return d
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    userInfo.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem(USER_KEY)
  }

  function restoreSession() {
    const at = localStorage.getItem('accessToken')
    const rt = localStorage.getItem('refreshToken')
    const ui = loadUserInfo()
    if (at) accessToken.value = at
    else accessToken.value = null
    if (rt) refreshToken.value = rt
    else refreshToken.value = null
    userInfo.value = ui
  }

  // ── private helpers ──

  function saveUserInfo(info: LoginResult['userInfo'] | null) {
    if (info) {
      localStorage.setItem(USER_KEY, JSON.stringify(info))
    }
  }

  function loadUserInfo(): LoginResult['userInfo'] | null {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }

  return { accessToken, refreshToken, userInfo, isAuthenticated, userName, userInitial, hasRole, login, refresh, logout, restoreSession }
})