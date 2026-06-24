import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDocumentSubgraph, getSubjectGraph, listSubjects } from '@/api/graph'
import { listFiles } from '@/api/file'
import type { GraphSubgraphVO, TextbookVO } from '@/api/types'

export type ViewMode = 'document' | 'subject'

export const useGraphStore = defineStore('graph', () => {
  const currentGraph = ref<GraphSubgraphVO | null>(null)
  const documents = ref<TextbookVO[]>([])
  const subjects = ref<string[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const currentDocId = ref<number | null>(null)
  const currentSubject = ref<string | null>(null)
  const viewMode = ref<ViewMode>('document')

  async function loadDocuments() {
    try {
      const result = await listFiles(1, 100)
      const graphStatuses = ['EXTRACTED', 'COMPLETED']
      documents.value = result.list.filter((d) => graphStatuses.includes(d.status))
    } catch {
      // 静默失败
    }
  }

  async function loadDocumentSubgraph(documentId: number) {
    loading.value = true
    error.value = null
    currentDocId.value = documentId
    setViewMode('document')
    try {
      currentGraph.value = await getDocumentSubgraph(documentId)
    } catch (e: any) {
      error.value = e?.userTip ?? '加载文档子图失败'
    } finally {
      loading.value = false
    }
  }

  async function loadSubjects() {
    try {
      subjects.value = await listSubjects()
    } catch {
      subjects.value = []
    }
  }

  async function loadSubjectGraph(subjectName: string) {
    loading.value = true
    error.value = null
    currentSubject.value = subjectName
    setViewMode('subject')
    try {
      const result = await getSubjectGraph(subjectName)
      // 防止旧请求返回覆盖已切换的状态
      if (currentSubject.value !== subjectName) return
      currentGraph.value = result
    } catch (e: any) {
      if (currentSubject.value !== subjectName) return
      error.value = e?._backendMessage ?? '加载学科全景图失败'
    } finally {
      if (currentSubject.value === subjectName) {
        loading.value = false
      }
    }
  }

  function setViewMode(mode: ViewMode) {
    viewMode.value = mode
    if (mode === 'subject') {
      currentDocId.value = null
    } else {
      currentSubject.value = null
    }
  }

  function clearGraph() {
    currentGraph.value = null
    currentDocId.value = null
    currentSubject.value = null
    viewMode.value = 'document'
    loading.value = false
  }

  return {
    currentGraph,
    documents,
    subjects,
    loading,
    error,
    currentDocId,
    currentSubject,
    viewMode,
    loadDocuments,
    loadDocumentSubgraph,
    loadSubjects,
    loadSubjectGraph,
    setViewMode,
    clearGraph,
  }
})