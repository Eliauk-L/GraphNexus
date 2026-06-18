import { defineStore } from 'pinia'
import { ref } from 'vue'
import { queryPageRank, queryDegree } from '@/api/graph'
import type { MetricResultVO } from '@/api/types'

export const useMetricsStore = defineStore('metrics', () => {
  const pagerankResults = ref<MetricResultVO[]>([])
  const degreeResults = ref<MetricResultVO[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const activeTab = ref<'pagerank' | 'degree'>('pagerank')

  async function loadPageRank(nodeTypes?: string[], edgeTypes?: string[]) {
    loading.value = true
    error.value = null
    try {
      pagerankResults.value = await queryPageRank(nodeTypes, edgeTypes)
    } catch (e) {
      error.value = '查询 PageRank 失败'
    } finally {
      loading.value = false
    }
  }

  async function loadDegree(nodeTypes?: string[], edgeTypes?: string[]) {
    loading.value = true
    error.value = null
    try {
      degreeResults.value = await queryDegree(nodeTypes, edgeTypes)
    } catch (e) {
      error.value = '查询度中心性失败'
    } finally {
      loading.value = false
    }
  }

  return {
    pagerankResults, degreeResults, loading, error, activeTab,
    loadPageRank, loadDegree,
  }
})