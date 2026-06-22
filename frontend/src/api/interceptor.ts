import axios from 'axios'
import client from '@/api/client'
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

/**
 * 给 axios 实例注册 Auth 拦截器。
 */
function registerAuthInterceptors(instance: typeof axios | typeof client) {
  // Request: 注入 Authorization header
  instance.interceptors.request.use((config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  // Response: 401 自动刷新
  instance.interceptors.response.use(
    (response) => response,
    async (error: any) => {
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
          return instance(originalRequest)
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

export function setupInterceptors() {
  // 项目既有 API 请求走 client（axios.create），全局 axios 仅用于登录/刷新
  registerAuthInterceptors(client)
  registerAuthInterceptors(axios)
}