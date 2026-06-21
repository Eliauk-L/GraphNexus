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

  async function loadDocuments() {
    try {
      const result = await listFiles(1, 100)
      // 已抽取/融合中/已完成状态的文档都有图谱数据可查看
const graphStatuses = ['EXTRACTED', 'EXTRACTING', 'FUSING', 'COMPLETED']
documents.value = result.list.filter((d) => graphStatuses.includes(d.status))
    } catch {
      // 静默失败
    }
  }

  async function loadSubgraph(documentId: number) {
    loading.value = true
    error.value = null
    try {
      currentGraph.value = await getDocumentSubgraph(documentId)
    } catch (e) {
      error.value = '加载子图失败'
    } finally {
      loading.value = false
    }
  }

  function clearGraph() {
    currentGraph.value = null
  }

  return { currentGraph, documents, loading, error, loadDocuments, loadSubgraph, clearGraph }
})