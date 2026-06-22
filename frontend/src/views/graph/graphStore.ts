import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDocumentSubgraph, fetchFullGraph } from '@/api/graph'
import { listFiles } from '@/api/file'
import type { GraphSubgraphVO, TextbookVO } from '@/api/types'

export type GraphViewMode = 'document' | 'full'

export const useGraphStore = defineStore('graph', () => {
  const currentGraph = ref<GraphSubgraphVO | null>(null)
  const documents = ref<TextbookVO[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const viewMode = ref<GraphViewMode>('document')
  const currentDocId = ref<number | null>(null)

  async function loadDocuments() {
    try {
      const result = await listFiles(1, 100)
      const graphStatuses = ['EXTRACTED', 'EXTRACTING', 'FUSING', 'COMPLETED']
      documents.value = result.list.filter((d) => graphStatuses.includes(d.status))
    } catch {
      // 静默失败
    }
  }

  async function loadDocumentSubgraph(documentId: number) {
    loading.value = true
    error.value = null
    viewMode.value = 'document'
    currentDocId.value = documentId
    try {
      currentGraph.value = await getDocumentSubgraph(documentId)
    } catch (e: any) {
      error.value = e?.userTip ?? '加载文档子图失败'
    } finally {
      loading.value = false
    }
  }

  async function loadFullGraph() {
    loading.value = true
    error.value = null
    viewMode.value = 'full'
    currentDocId.value = null
    try {
      currentGraph.value = await fetchFullGraph()
    } catch (e: any) {
      error.value = e?.userTip ?? '加载全量图谱失败'
    } finally {
      loading.value = false
    }
  }

  function clearGraph() {
    currentGraph.value = null
    currentDocId.value = null
  }

  return {
    currentGraph,
    documents,
    loading,
    error,
    viewMode,
    currentDocId,
    loadDocuments,
    loadDocumentSubgraph,
    loadFullGraph,
    clearGraph,
  }
})