import { defineStore } from 'pinia'
import { ref } from 'vue'
import { uploadFile, listFiles, getFile, processFile, deleteFile } from '@/api/file'
import type { DocumentVO } from '@/api/types'

export const useFileStore = defineStore('file', () => {
  const files = ref<DocumentVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function loadFiles(pageNum = 1, pageSize = 10) {
    loading.value = true
    error.value = null
    try {
      const result = await listFiles(pageNum, pageSize)
      files.value = result.list
      total.value = result.total
    } catch (e) {
      error.value = '加载文件列表失败'
    } finally {
      loading.value = false
    }
  }

  async function upload(file: File, subject: string) {
    loading.value = true
    error.value = null
    try {
      const result = await uploadFile(file, subject)
      await loadFiles()
      return result
    } catch (e) {
      error.value = '上传失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function process(id: number) {
    error.value = null
    try {
      const result = await processFile(id)
      // 刷新列表中该文件状态
      await loadFiles()
      return result
    } catch (e) {
      error.value = '解析失败'
      throw e
    }
  }

  async function remove(id: number) {
    error.value = null
    try {
      await deleteFile(id)
      await loadFiles()
    } catch (e) {
      error.value = '删除失败'
      throw e
    }
  }

  return { files, total, loading, error, loadFiles, upload, process, remove }
})