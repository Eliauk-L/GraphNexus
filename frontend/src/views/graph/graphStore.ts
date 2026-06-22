import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getDocumentSubgraph, fetchFullGraph } from '@/api/graph'
import { listFiles } from '@/api/file'
import type { GraphSubgraphVO, TextbookVO, FileStatus } from '@/api/types'

export type GraphViewMode = 'document' | 'full'

/** 可加载子图的文档状态（已抽取或已完成） */
const GRAPH_READY_STATES: FileStatus[] = ['EXTRACTED', 'COMPLETED']

/** 处理中的文档状态（有子图但可能不完整） */
const PROCESSING_STATES: FileStatus[] = ['EXTRACTING', 'FUSING']

export const useGraphStore = defineStore('graph', () => {
  const currentGraph = ref<GraphSubgraphVO | null>(null)
  const documents = ref<TextbookVO[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const viewMode = ref<GraphViewMode>('document')
  const currentDocId = ref<number | null>(null)

  /** 当前选中文档的状态 */
  const selectedDocStatus = computed<FileStatus | null>(() => {
    if (!currentDocId.value) return null
    const doc = documents.value.find((d) => d.documentId === currentDocId.value)
    return doc?.status ?? null
  })

  /** 当前选中文档是否为非终态（EXTRACTED 但未融合完成） */
  const isSelectedDocNonTerminal = computed(() =>
    selectedDocStatus.value === 'EXTRACTED',
  )

  async function loadDocuments() {
    try {
      const result = await listFiles(1, 100)
      // 加载所有处于抽取阶段及之后的文档（含处理中）
      const visibleStates: FileStatus[] = ['EXTRACTING', 'EXTRACTED', 'FUSING', 'COMPLETED']
      documents.value = result.list.filter((d) => visibleStates.includes(d.status))
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
    selectedDocStatus,
    isSelectedDocNonTerminal,
    loadDocuments,
    loadDocumentSubgraph,
    loadFullGraph,
    clearGraph,
  }
})