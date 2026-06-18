import axios from 'axios'
import type { ApiResult } from './types'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// request 拦截器：注入 traceId（V1 预留）
client.interceptors.request.use((config) => {
  const traceId = sessionStorage.getItem('traceId')
  if (traceId) {
    config.headers['X-Trace-Id'] = traceId
  }
  return config
})

// response 拦截器：unwrap ApiResult.data + 错误 toast
client.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    // 直接返回 data 字段，调用方拿到的是 unwrap 后的业务数据
    return body.data as never
  },
  (error) => {
    // 从 error.response.data 取 ErrorResponse
    const errData = error.response?.data
    if (errData?.userTip) {
      import('naive-ui').then(({ useMessage }) => {
        useMessage().error(errData.userTip as string)
      }).catch(() => {})
    }
    console.error(
      `[API] ${errData?.errorCode ?? 'NETWORK_ERROR'}: ${errData?.errorMessage ?? error.message}`,
      `(traceId: ${errData?.traceId ?? 'N/A'})`,
    )
    return Promise.reject(error)
  },
)

export default client