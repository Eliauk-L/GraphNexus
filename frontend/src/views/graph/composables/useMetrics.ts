/**
 * useMetrics — 度量数据管理 composable。
 *
 * 管理度中心性和 PageRank 数据的加载、缓存和开关状态。
 * PageRank 开关持久化到 localStorage（key: 'graphviz.pagerank'），默认关闭。
 *
 * 参照 useGraphInteraction 的同目录、同风格。
 */
import { ref } from 'vue'
import { queryDegree, queryPageRank } from '@/api/graph'
import type { MetricResultVO } from '@/api/types'

const STORAGE_KEY = 'graphviz.pagerank'

function readStoredPagerankEnabled(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false  // 隐私模式降级
  }
}

function writeStoredPagerankEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(enabled))
  } catch {
    // 隐私模式静默降级
  }
}

export function useMetrics() {
  const degreeData = ref<MetricResultVO[]>([])
  const pagerankData = ref<MetricResultVO[]>([])
  const pagerankEnabled = ref(readStoredPagerankEnabled())
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function loadDegreeMetrics(subject: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      degreeData.value = await queryDegree(['KnowledgePoint'], undefined, subject)
    } catch (e: any) {
      error.value = e?._backendMessage ?? '度量数据加载失败'
      // AC-10 降级：不抛异常，度量为空时图谱用默认样式
      degreeData.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadPageRankMetrics(subject: string): Promise<void> {
    try {
      pagerankData.value = await queryPageRank(['KnowledgePoint'], undefined, subject)
    } catch (e: any) {
      // PageRank 加载失败不阻塞（AC-10），静默降级
      pagerankData.value = []
    }
  }

  function togglePageRank(): void {
    pagerankEnabled.value = !pagerankEnabled.value
    writeStoredPagerankEnabled(pagerankEnabled.value)
  }

  function getNodeDegree(nodeId: string): { inDegree: number; outDegree: number; totalDegree: number } {
    let inDeg = 0
    let outDeg = 0
    for (const d of degreeData.value) {
      if (d.nodeId === nodeId) {
        if (d.metricName === 'inDegree') inDeg = d.metricValue
        else if (d.metricName === 'outDegree') outDeg = d.metricValue
      }
    }
    return { inDegree: inDeg, outDegree: outDeg, totalDegree: inDeg + outDeg }
  }

  function getNodePageRank(nodeId: string): number | null {
    const entry = pagerankData.value.find((p) => p.nodeId === nodeId)
    return entry ? entry.metricValue : null
  }

  return {
    degreeData,
    pagerankData,
    pagerankEnabled,
    loading,
    error,
    loadDegreeMetrics,
    loadPageRankMetrics,
    togglePageRank,
    getNodeDegree,
    getNodePageRank,
  }
}