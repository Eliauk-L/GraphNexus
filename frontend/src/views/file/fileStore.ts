import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { uploadFile, listFiles, parseFile, deleteFile } from '@/api/file'
import type { TextbookVO, FileStatus } from '@/api/types'

// 需要轮询的中间态
const INTERMEDIATE_STATES: FileStatus[] = ['UPLOADED', 'PARSING']

export const useFileStore = defineStore('file', () => {
  const files = ref<TextbookVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 轮询相关
  let pollingTimer: ReturnType<typeof setInterval> | null = null
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
    try {
      const result = await parseFile(id)
      await loadFiles()
      startPolling()
      return result
    } catch {
      error.value = '解析失败'
      throw new Error('解析失败')
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

  /** 开启状态轮询（若已开启则跳过） */
  function startPolling() {
    if (pollingTimer) return
    isPolling.value = true
    pollingTimer = setInterval(async () => {
      // 无中间态文件 → 停止轮询
      if (!hasIntermediateFiles.value) {
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
    isPolling.value = false
  }

  return {
    files, total, loading, error, isPolling, hasIntermediateFiles,
    loadFiles, upload, parse, remove, startPolling, stopPolling,
  }
})