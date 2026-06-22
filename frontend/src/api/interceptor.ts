import axios from 'axios'
import client from '@/api/client'
import router from '@/router'

const TOKEN_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'
const EXPIRES_KEY = 'tokenExpiresAt'

/** 提前多久刷新（毫秒） */
const REFRESH_AHEAD_MS = 60_000

let isRefreshing = false
let refreshPromise: Promise<any> | null = null
let refreshTimer: ReturnType<typeof setTimeout> | null = null

// ── localStorage helpers ──

function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

function getExpiresAt(): number | null {
  const v = localStorage.getItem(EXPIRES_KEY)
  return v ? Number(v) : null
}

function saveAuth(access: string, refresh: string, expiresIn: number) {
  localStorage.setItem(TOKEN_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
  localStorage.setItem(EXPIRES_KEY, String(Date.now() + expiresIn * 1000))
}

function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(EXPIRES_KEY)
  clearRefreshTimer()
}

// ── 主动刷新定时器 ──

function clearRefreshTimer() {
  if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null }
}

function scheduleProactiveRefresh(expiresIn: number) {
  clearRefreshTimer()
  const delay = Math.max(0, expiresIn * 1000 - REFRESH_AHEAD_MS)
  if (delay <= 0) {
    // Token 已接近过期，立即刷新
    doSilentRefresh()
  } else {
    refreshTimer = setTimeout(doSilentRefresh, delay)
  }
}

async function doSilentRefresh() {
  const rt = getRefreshToken()
  if (!rt) return
  try {
    const res = await axios.post('/api/v1/auth/refresh', { refreshToken: rt })
    const d = res.data.data
    saveAuth(d.accessToken, d.refreshToken, d.expiresIn)
    scheduleProactiveRefresh(d.expiresIn)
  } catch {
    // 刷新失败不打扰用户，等下次 401 兜底
  }
}

/** 页面加载时启动主动刷新定时器 */
export function startProactiveRefresh() {
  const expiresAt = getExpiresAt()
  if (!expiresAt) return
  const remaining = expiresAt - Date.now()
  if (remaining <= 0) return // 已经过期，等 401 兜底
  if (remaining <= REFRESH_AHEAD_MS) {
    doSilentRefresh()
  } else {
    scheduleProactiveRefresh(remaining / 1000)
  }
}

// ── axios 拦截器 ──

function registerAuthInterceptors(instance: typeof axios | typeof client) {
  instance.interceptors.request.use((config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  instance.interceptors.response.use(
    (response) => response,
    async (error: any) => {
      const originalRequest = error.config

      if (
        (error.response?.status === 401 || error.response?.status === 403) &&
        !originalRequest._retry &&
        !originalRequest.url?.includes('/api/v1/auth/refresh')
      ) {
        const rt = getRefreshToken()
        if (!rt) {
          clearAuth()
          router.push('/login')
          return Promise.reject(error)
        }

        if (!isRefreshing) {
          isRefreshing = true
          refreshPromise = axios.post('/api/v1/auth/refresh', { refreshToken: rt })
            .then((res) => {
              const d = res.data.data
              saveAuth(d.accessToken, d.refreshToken, d.expiresIn)
              scheduleProactiveRefresh(d.expiresIn)
              return d
            })
            .finally(() => {
              isRefreshing = false
              refreshPromise = null
            })
        }

        try {
          await refreshPromise
          originalRequest._retry = true
          originalRequest.headers.Authorization = `Bearer ${getToken()}`
          return instance(originalRequest)
        } catch {
          clearAuth()
          router.push({ path: '/login', query: { expired: 'true' } })
          return Promise.reject(error)
        }
      }

      return Promise.reject(error)
    },
  )
}

export function setupInterceptors() {
  registerAuthInterceptors(client)
  registerAuthInterceptors(axios)
  startProactiveRefresh()
}