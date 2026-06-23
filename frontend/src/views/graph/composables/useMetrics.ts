/**
 * useMetrics — 度量数据管理 composable（v2 · 仅考试频次）。
 *
 * 度中心性和 PageRank 后端保留但前端不再展示，
 * 当前仅通过考试频次反映知识点在实际考试中的重要程度。
 */
import { ref } from 'vue'
import { queryExamFrequency } from '@/api/graph'
import type { MetricResultVO } from '@/api/types'

export function useMetrics() {
  const examFrequencyData = ref<MetricResultVO[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function loadExamFrequency(subject?: string, documentId?: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      examFrequencyData.value = await queryExamFrequency(subject, documentId)
    } catch (e: any) {
      error.value = e?._backendMessage ?? '考试频次加载失败'
      examFrequencyData.value = []
    } finally {
      loading.value = false
    }
  }

  function getNodeExamFrequency(nodeId: string): number {
    const entry = examFrequencyData.value.find((e) => e.nodeId === nodeId)
    return entry ? entry.metricValue : 0
  }

  return {
    examFrequencyData,
    loading,
    error,
    loadExamFrequency,
    getNodeExamFrequency,
  }
}