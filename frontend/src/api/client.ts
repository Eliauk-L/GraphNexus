import axios from 'axios'
import { createDiscreteApi } from 'naive-ui'
import type { ApiResult, ErrorResponse } from './types'

// 组件外可用的 message 实例（适用于 Axios 拦截器等非组件上下文）
const { message } = createDiscreteApi(['message'])

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 网络错误防抖：避免短时间内多条请求同时失败时重复弹 toast
let lastNetworkErrorTime = 0
const NETWORK_ERROR_DEBOUNCE = 5000

// request 拦截器：注入 traceId（V1 预留）
client.interceptors.request.use((config) => {
  const traceId = sessionStorage.getItem('traceId')
  if (traceId) {
    config.headers['X-Trace-Id'] = traceId
  }
  return config
})

// 业务错误码 → 用户友好提示映射（来源：后端 ErrorCode.java）
const FRIENDLY_TIPS: Record<string, string> = {
  A0001: '请求的资源不存在，请检查参数',
  A0002: '请求参数不符合要求，请检查输入',
  A0003: '您没有权限执行此操作',
  A0004: '仅支持 PDF 或 TXT 格式文件',
  A0005: '文件大小不能超过 50MB',
  A0006: '文档记录不存在或已被删除',
  A0007: '该学科下已存在相同内容的文档',
  A0008: '文档文本内容为空，无法进行图谱抽取',
  A0009: '文档状态不允许抽取，请先完成文档解析',
  A0010: 'AI 返回结果格式不符合预期，请稍后重试',
  A0011: 'CSV 文件格式不符合要求，请检查表头和成绩格式',
  A0012: 'CSV 文件缺少必要列（学号或考试编号），请检查文件',
  A0013: 'CSV 文件编码不支持，请使用 UTF-8 或 GBK 编码',
  A0014: '考试编号不存在，无法执行操作',
  A0015: '待删除的考试记录不存在或已被删除',
  A0016: '融合日志记录不存在，请检查融合 ID',
  A0017: '融合操作正在进行中，请稍后重试',
  A0018: '图状态已发生变更，无法回滚到指定融合点',
  A0019: '无法识别查询意图，请更明确地描述问题',
  A0020: '存在多个同名或相似学生，请使用学号精确指定',
  A0021: '问答任务不存在或已过期',
  A0022: '考试编号已存在，请先删除该考试再重新上传',
  B0001: '系统内部异常，请联系管理员',
  B0002: '服务暂时不可用，请稍后重试',
  C0001: '外部服务调用失败，请稍后重试',
}

// response 拦截器：unwrap ApiResult.data + 用户友好错误提示
client.interceptors.response.use(
  (response) => {
    // blob 响应（文件下载等）跳过 unwrap，直接返回
    if (response.config.responseType === 'blob') {
      return response.data as never
    }
    const body = response.data as ApiResult<unknown>
    return body.data as never
  },
  (error) => {
    const errData: ErrorResponse | undefined = error.response?.data
    const errorCode = errData?.errorCode

    // 判断是否为后端服务不可达（Vite proxy ECONNREFUSED / 网络不通）
    // 注意：有 errorCode 的响应（即使 status>=502）是业务异常，不走 isServerDown
    const isServerDown =
      !error.response ||
      error.code === 'ECONNREFUSED' ||
      error.code === 'ERR_BAD_RESPONSE' ||
      (error.response?.status && error.response.status >= 502 && !errorCode)

    // 请求超时：由调用方自己处理（如学情诊断大模型重试），不弹全局 toast
    const isTimeout = error.code === 'ECONNABORTED'

    if (isServerDown) {
      const now = Date.now()
      if (now - lastNetworkErrorTime > NETWORK_ERROR_DEBOUNCE) {
        lastNetworkErrorTime = now
        message.error('后端服务未启动或网络异常，请检查服务状态', {
          duration: 8000,
          closable: true,
        })
      }
    } else if (isTimeout) {
      // 超时由调用方处理（如学情诊断大模型重试），不弹全局 toast
    } else if (errorCode) {
      // 业务错误：优先用后端 userTip，其次用 errorMessage，回退到错误码映射
      const friendlyTip = errData?.userTip || errData?.errorMessage || FRIENDLY_TIPS[errorCode] || '操作失败，请稍后重试'
      showErrorToast(friendlyTip, errorCode)
      // 将后端错误信息注入 rejected error，供调用方（如 queryStore）展示详情
      error._backendMessage = friendlyTip
      error._backendCode = errorCode
    } else {
      // 其他 HTTP 错误
      const status = error.response.status
      const tip = HTTP_STATUS_TIPS[status] ?? `请求失败（HTTP ${status}）`
      showErrorToast(tip, `HTTP_${status}`)
      error._backendMessage = tip
    }

    // 控制台完整日志（含 traceId 便于排查）
    console.error(
      `[API] ${errorCode ?? 'UNKNOWN'}: ${errData?.errorMessage ?? error.message}`,
      `(traceId: ${errData?.traceId ?? 'N/A'})`,
    )
    return Promise.reject(error)
  },
)

const HTTP_STATUS_TIPS: Record<number, string> = {
  400: '请求参数有误，请检查输入',
  401: '登录状态已失效，请重新登录',
  403: '您没有权限执行此操作',
  404: '请求的资源不存在',
  409: '操作冲突，请刷新后重试',
  413: '文件大小超过限制',
  500: '服务器内部错误，请联系管理员',
  502: '网关错误，请稍后重试',
  503: '服务暂时不可用，请稍后重试',
  504: '网关超时，请稍后重试',
}

function showErrorToast(tip: string, code: string) {
  // 错误码前缀决定提示类型与持续时间
  const isSystemError = code.startsWith('B') || code.startsWith('C') || code.startsWith('HTTP_5')
  if (isSystemError) {
    message.error(tip, { duration: 5000 })
  } else {
    message.warning(tip, { duration: 3500 })
  }
}

export default client