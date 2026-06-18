import { defineStore } from 'pinia'
import { ref } from 'vue'
import { uploadFile, listFiles, processFile, deleteFile } from '@/api/file'
import type { FileVO } from '@/api/types'

export const useFileStore = defineStore('file', () => {
  const files = ref<FileVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

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

  async function upload(file: File, subject: string) {
    loading.value = true
    error.value = null
    try {
      // 阶段一：上传文件（仅存储入库，返回 status=UPLOADED + filePath）
      const result = await uploadFile(file, subject)
      // 阶段二：触发处理链路（解析→抽取→融合）
      await processFile(result.documentId)
      await loadFiles()
      return result
    } catch {
      error.value = '上传或处理失败'
      throw new Error('上传或处理失败')
    } finally {
      loading.value = false
    }
  }

  async function process(id: number) {
    error.value = null
    try {
      const result = await processFile(id)
      await loadFiles()
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

  return { files, total, loading, error, loadFiles, upload, process, remove }
})