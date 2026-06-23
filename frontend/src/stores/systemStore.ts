import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getSystemHealth,
  getLogFiles,
  getLogContent,
  downloadLogFile,
  type SystemHealthVO,
  type LogFileVO,
  type LogContentVO,
} from '@/api/system'

export const useSystemStore = defineStore('system', () => {
  const health = ref<SystemHealthVO | null>(null)
  const logFiles = ref<LogFileVO[]>([])
  const currentLog = ref<LogContentVO | null>(null)
  const loading = ref(false)
  const lastRefreshTime = ref<string | null>(null)

  let refreshTimer: ReturnType<typeof setInterval> | null = null

  async function fetchHealth() {
    try {
      const res = await getSystemHealth()
      health.value = (res as any).data?.data ?? (res as any).data ?? res
      lastRefreshTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    } catch (e) {
      console.error('[systemStore] 获取健康状态失败:', e)
    }
  }

  async function fetchLogFiles() {
    loading.value = true
    try {
      const res = await getLogFiles()
      logFiles.value = (res as any).data?.data ?? (res as any).data ?? res
    } catch (e) {
      console.error('[systemStore] 获取日志文件列表失败:', e)
    } finally {
      loading.value = false
    }
  }

  async function fetchLogContent(filename: string, page = 1, size = 200) {
    loading.value = true
    try {
      const res = await getLogContent(filename, page, size)
      currentLog.value = (res as any).data?.data ?? (res as any).data ?? res
    } catch (e) {
      console.error('[systemStore] 获取日志内容失败:', e)
    } finally {
      loading.value = false
    }
  }

  async function downloadLog(filename: string) {
    try {
      await downloadLogFile(filename)
    } catch (e) {
      console.error('[systemStore] 下载日志失败:', e)
    }
  }

  /** 启动 30s 自动轮询健康状态 */
  function startHealthPolling() {
    fetchHealth()
    refreshTimer = setInterval(fetchHealth, 30_000)
  }

  /** 停止轮询 */
  function stopHealthPolling() {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }

  return {
    health,
    logFiles,
    currentLog,
    loading,
    lastRefreshTime,
    fetchHealth,
    fetchLogFiles,
    fetchLogContent,
    downloadLog,
    startHealthPolling,
    stopHealthPolling,
  }
})