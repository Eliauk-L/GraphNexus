import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDocumentSubgraph } from '@/api/graph'
import { listFiles } from '@/api/file'
import type { GraphSubgraphVO, TextbookVO } from '@/api/types'

export const useGraphStore = defineStore('graph', () => {
  const currentGraph = ref<GraphSubgraphVO | null>(null)
  const documents = ref<TextbookVO[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
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
    currentDocId.value = documentId
    try {
      currentGraph.value = await getDocumentSubgraph(documentId)
    } catch (e: any) {
      error.value = e?.userTip ?? '加载文档子图失败'
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
    currentDocId,
    loadDocuments,
    loadDocumentSubgraph,
    clearGraph,
  }
})