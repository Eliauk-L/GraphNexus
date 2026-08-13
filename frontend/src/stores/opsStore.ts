import { defineStore } from 'pinia'
import { ref } from 'vue'
import { opsApi, OpsSummaryResponse, OpsTrendResponse } from '@/api/ops'

/**
 * 运营仪表盘 Pinia 状态管理。
 * 管理摘要数据、趋势数据、时间粒度和学科选择。
 */
export const useOpsStore = defineStore('ops', () => {
  // ── state ──
  const summary = ref<OpsSummaryResponse | null>(null)
  const trend = ref<OpsTrendResponse | null>(null)
  const subjects = ref<string[]>([])
  const granularity = ref<'day' | 'week' | 'month'>('month')
  const subject = ref('全部学科')
  const loading = ref(false)
  const trendLoading = ref(false)
  const error = ref<string | null>(null)

  let lastSummaryFetch = 0
  const SUMMARY_CACHE_MS = 60_000 // 60s 缓存

  // ── actions ──
  async function fetchSummary(force = false) {
    const now = Date.now()
    if (!force && summary.value && now - lastSummaryFetch < SUMMARY_CACHE_MS) return

    loading.value = true
    error.value = null
    try {
      const res = await opsApi.getSummary()
      summary.value = res
      lastSummaryFetch = now
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '加载摘要数据失败'
      error.value = msg
      console.error('[opsStore] fetchSummary failed:', msg)
    } finally {
      loading.value = false
    }
  }

  async function fetchTrend(metric: string, range = 30) {
    trendLoading.value = true
    try {
      const res = await opsApi.getTrend(metric, granularity.value, range)
      trend.value = res
    } catch (e: unknown) {
      console.error('[opsStore] fetchTrend failed:', e)
    } finally {
      trendLoading.value = false
    }
  }

  async function fetchSubjects() {
    try {
      const res = await opsApi.getSubjects()
      subjects.value = Array.isArray(res) ? res : []
    } catch (e: unknown) {
      console.error('[opsStore] fetchSubjects failed:', e)
    }
  }

  function setGranularity(g: 'day' | 'week' | 'month') {
    granularity.value = g
    fetchSummary(true)
  }

  function setSubject(s: string) {
    subject.value = s
    fetchSummary(true)
  }

  return {
    summary, trend, subjects, granularity, subject,
    loading, trendLoading, error,
    fetchSummary, fetchTrend, fetchSubjects,
    setGranularity, setSubject,
  }
})
