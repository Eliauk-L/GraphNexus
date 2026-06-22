import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { uploadFile, listFiles, parseFile, deleteFile } from '@/api/file'
import { extractGraph } from '@/api/graph'
import type { TextbookVO, FileStatus } from '@/api/types'

// 仅追踪活跃处理态（*ING），排除稳定态（UPLOADED/PARSED/EXTRACTED）避免空转
const INTERMEDIATE_STATES: FileStatus[] = ['PARSING', 'EXTRACTING', 'FUSING']

/** 最大轮询时长（毫秒），超时强制停止，防止异常情况空转 */
const MAX_POLLING_DURATION = 5 * 60 * 1000 // 5 分钟

/** 最小轮询窗口（毫秒），桥接 UPLOADED→PARSING 和 PARSED→EXTRACTING 等过渡间隙 */
const MIN_POLLING_DURATION = 30 * 1000 // 30 秒

export const useFileStore = defineStore('file', () => {
  const files = ref<TextbookVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 轮询相关
  let pollingTimer: ReturnType<typeof setInterval> | null = null
  let pollingStartTime: number | null = null
  const isPolling = ref(false)

  // 是否存在中间态文件（决定是否继续轮询）
  const hasIntermediateFiles = computed(() =>
    files.value.some((f) => INTERMEDIATE_STATES.includes(f.status)),
  )

  async function loadFiles(pageNum = 1, pageSize = 10, fileType?: string, name?: string) {
    loading.value = true
    error.value = null
    try {
      const result = await listFiles(pageNum, pageSize, fileType, name)
      files.value = result.list
      total.value = result.total
    } catch {
      error.value = '加载文件列表失败'
    } finally {
      loading.value = false
    }
  }

  /**
   * 上传文件：完成上传后立即返回，后台触发解析 + 开启状态轮询。
   * 解析完成后状态从 UPLOADED → PARSED，后续图谱构建由后端事件驱动。
   */
  async function upload(file: File, subject: string) {
    loading.value = true
    error.value = null
    let uploaded: TextbookVO
    try {
      uploaded = await uploadFile(file, subject)
    } catch {
      error.value = '上传失败'
      throw new Error('上传失败')
    } finally {
      loading.value = false
    }

    // 立即刷新列表，展示刚上传的文件（UPLOADED 状态）
    await loadFiles()

    // 后台触发解析，失败时提示用户手动重试
    parseFile(uploaded.documentId)
      .then(() => loadFiles())
      .catch(() => {
        error.value = '解析失败，请手动点击解析按钮重试'
      })

    // 开启状态轮询（追踪后续状态变化）
    startPolling()
    return uploaded
  }

  async function parse(id: number) {
    error.value = null
    // 先启动轮询，再调用 API — 确保 API 处理期间前端能感知中间态（PARSING）
    startPolling()
    try {
      const result = await parseFile(id)
      await loadFiles()
      return result
    } catch {
      error.value = '解析失败'
      throw new Error('解析失败')
    }
  }

  /** 触发图谱构建（抽取+融合），用于手动重试图谱构建。
   *
   * 先启动轮询再调用 API，确保 API 同步处理期间（可达数十秒）
   * 前端能实时看到 EXTRACTING → EXTRACTED → FUSING → COMPLETED 的完整流转。 */
  async function extract(id: number) {
    error.value = null
    // 先启动轮询 — API 处理期间后端 saveAndFlush 的中间态可被轮询读取
    startPolling()
    try {
      await extractGraph(id)
      await loadFiles()
    } catch {
      error.value = '图谱构建失败'
      throw new Error('图谱构建失败')
    }
  }

  async function remove(id: number) {
    error.value = null
    try {
      await deleteFile(id)
      await loadFiles()
    } catch {
      error.value = '删除失败'
      throw new Error('删除失败')
    }
  }

  /** 开启状态轮询（若已开启则跳过）。
   *
   * 仅追踪活跃处理态（PARSING/EXTRACTING/FUSING），稳定态不触发轮询。
   * 超过 MAX_POLLING_DURATION 强制停止，防止异常情况空转。 */
  function startPolling() {
    if (pollingTimer) return
    isPolling.value = true
    pollingStartTime = Date.now()
    pollingTimer = setInterval(async () => {
      const elapsed = pollingStartTime ? Date.now() - pollingStartTime : 0
      // 超过最大轮询时长 → 强制停止
      if (elapsed > MAX_POLLING_DURATION) {
        stopPolling()
        return
      }
      // 最小窗口内持续轮询，桥接 UPLOADED→PARSING / PARSED→EXTRACTING 过渡间隙
      const inMinWindow = elapsed < MIN_POLLING_DURATION
      if (!inMinWindow && !hasIntermediateFiles.value) {
        stopPolling()
        return
      }
      // 静默刷新（不触发 loading 闪烁）
      try {
        const result = await listFiles(1, 100)
        files.value = result.list
        total.value = result.total
      } catch {
        // 静默失败，下次轮询继续
      }
    }, 2000)
  }

  /** 停止状态轮询 */
  function stopPolling() {
    if (pollingTimer) {
      clearInterval(pollingTimer)
      pollingTimer = null
    }
    pollingStartTime = null
    isPolling.value = false
  }

  return {
    files, total, loading, error, isPolling, hasIntermediateFiles,
    loadFiles, upload, parse, extract, remove, startPolling, stopPolling,
  }
})