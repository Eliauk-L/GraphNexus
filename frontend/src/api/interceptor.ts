import axios from 'axios'
import router from '@/router'

const TOKEN_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

let isRefreshing = false
let refreshPromise: Promise<any> | null = null

function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

function setTokens(access: string, refresh: string) {
  localStorage.setItem(TOKEN_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
}

function clearTokens() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

export function setupInterceptors() {
  // Request: 注入 Authorization header（直接读 localStorage，避免 Pinia 时序问题）
  axios.interceptors.request.use((config) => {
    const token = getToken()
    console.log('[Interceptor] request:', config.url, { hasToken: !!token })
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  // Response: 401 自动刷新
  axios.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config

      if (
        error.response?.status === 401 &&
        !originalRequest._retry &&
        !originalRequest.url?.includes('/api/v1/auth/refresh')
      ) {
        const refreshToken = getRefreshToken()
        if (!refreshToken) {
          clearTokens()
          router.push('/login')
          return Promise.reject(error)
        }

        // 刷新锁：同一时刻只有一个 refresh 请求
        if (!isRefreshing) {
          isRefreshing = true
          refreshPromise = axios.post('/api/v1/auth/refresh', { refreshToken })
            .then((res) => {
              const d = res.data.data
              setTokens(d.accessToken, d.refreshToken)
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
          return axios(originalRequest)
        } catch {
          clearTokens()
          router.push({ path: '/login', query: { expired: 'true' } })
          return Promise.reject(error)
        }
      }

      return Promise.reject(error)
    },
  )
}