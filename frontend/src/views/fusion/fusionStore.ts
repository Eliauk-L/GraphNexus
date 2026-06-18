import { defineStore } from 'pinia'
import { ref } from 'vue'
import { executeFusion, getFusionStatus, rollbackFusion } from '@/api/graph'
import type { FusionExecuteVO, FusionStatusVO, FusionRollbackVO } from '@/api/types'

export const useFusionStore = defineStore('fusion', () => {
  const lastStatus = ref<FusionStatusVO | null>(null)
  const loading = ref(false)
  const executing = ref(false)
  const error = ref<string | null>(null)

  async function execute() {
    executing.value = true
    error.value = null
    try {
      const result = await executeFusion()
      await loadStatus()
      return result
    } catch (e: any) {
      const msg = e?.response?.data?.userTip ?? '融合执行失败'
      error.value = msg
      throw e
    } finally {
      executing.value = false
    }
  }

  async function loadStatus() {
    loading.value = true
    error.value = null
    try {
      lastStatus.value = await getFusionStatus()
    } catch (e) {
      error.value = '加载融合状态失败'
    } finally {
      loading.value = false
    }
  }

  async function rollback(logId: number) {
    loading.value = true
    error.value = null
    try {
      const result = await rollbackFusion(logId)
      await loadStatus()
      return result
    } catch (e: any) {
      const msg = e?.response?.data?.userTip ?? '回滚失败'
      error.value = msg
      throw e
    } finally {
      loading.value = false
    }
  }

  return { lastStatus, loading, executing, error, execute, loadStatus, rollback }
})