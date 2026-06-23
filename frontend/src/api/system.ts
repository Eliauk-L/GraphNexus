import client from './client'

/** 组件健康状态 */
export interface ComponentHealth {
  name: string
  status: 'UP' | 'DOWN'
  latency: number | null
  error: string | null
}

/** JVM 运行时指标 */
export interface JvmMetrics {
  heapUsed: number
  heapMax: number
  cpuUsage: number
  threadCount: number
  gcCount: number
}

/** 系统健康响应 */
export interface SystemHealthVO {
  components: ComponentHealth[]
  jvm: JvmMetrics
}

/** 日志文件元数据 */
export interface LogFileVO {
  fileName: string
  fileSize: number
  fileSizeFormatted: string
  lastModified: string
}

/** 日志分页内容 */
export interface LogContentVO {
  fileName: string
  lines: string[]
  currentPage: number
  totalPages: number
  totalLines: number
  pageSize: number
}

/**
 * 获取系统健康状态（组件 + JVM 指标）。
 */
export function getSystemHealth(): Promise<SystemHealthVO> {
  return client.get('/system/health')
}

/**
 * 列出所有日志文件。
 */
export function getLogFiles(): Promise<LogFileVO[]> {
  return client.get('/system/logs')
}

/**
 * 分页读取日志文件内容。
 */
export function getLogContent(
  filename: string,
  page = 1,
  size = 200
): Promise<LogContentVO> {
  return client.get(`/system/logs/${encodeURIComponent(filename)}`, {
    params: { page, size },
  })
}

/**
 * 下载日志文件（触发浏览器下载）。
 */
export async function downloadLogFile(filename: string): Promise<void> {
  const res = await client.get(`/system/logs/${encodeURIComponent(filename)}/download`, {
    responseType: 'blob',
  })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}