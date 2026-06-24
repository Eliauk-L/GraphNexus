import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chat, askAsync, getResult, getHistory, exportSingle, deleteHistory } from '@/api/query'
import { getPrunedSubgraph } from '@/api/analysis'
import type { QueryAskResponse, QueryResultResponse, TokenUsageVO, HistoryRecordVO, HistoryQueryParams, SubgraphResponse } from '@/api/types'

export const useQueryStore = defineStore('query', () => {
  const currentQuestion = ref('')
  const answer = ref('')
  const taskId = ref('')
  const intent = ref('')
  const outputFormat = ref<string>('markdown')
  const status = ref<'idle' | 'pending' | 'processing' | 'completed' | 'failed'>('idle')
  const tokenUsage = ref<TokenUsageVO | null>(null)
  const errorMessage = ref('')
  const history = ref<Array<{ question: string; answer: string; taskId: string }>>([])

  let pollingTimer: ReturnType<typeof setInterval> | null = null

  // ── 子图可视化状态 ──
  const subgraphData = ref<SubgraphResponse | null>(null)
  const subgraphState = ref<'idle' | 'loading' | 'loaded' | 'error'>('idle')
  const selectedKpNode = ref<{ id: string; label: string; weight?: number; examHistory?: string } | null>(null)

  // ── 历史记录状态 ──
  const historyRecords = ref<HistoryRecordVO[]>([])
  const historyTotal = ref(0)
  const historyLoading = ref(false)
  const historyPage = ref(1)
  const historyPageSize = ref(10)
  const historyFilters = ref<HistoryQueryParams>({})

  async function sendChat(question: string) {
    currentQuestion.value = question
    status.value = 'pending'
    errorMessage.value = ''

    try {
      const result = await chat({ question })
      handleResult(result)
    } catch (e: any) {
      status.value = 'failed'
      if (e?.code === 'ECONNABORTED') {
        errorMessage.value = '分析请求超时，大模型可能正在处理中，请稍后重试'
      } else if (e?._backendMessage) {
        errorMessage.value = e._backendMessage
      } else {
        errorMessage.value = '分析请求失败，请稍后重试'
      }
    }
  }

  async function sendAsync(question: string, studentName: string, subject: string) {
    currentQuestion.value = question
    status.value = 'pending'
    errorMessage.value = ''

    try {
      const asyncRes = await askAsync({ question, studentName, subject })
      taskId.value = asyncRes.taskId
      startPolling(asyncRes.taskId)
    } catch (e) {
      status.value = 'failed'
      errorMessage.value = '提交失败'
    }
  }

  function startPolling(tid: string) {
    stopPolling()
    status.value = 'processing'
    let retries = 0
    const MAX_RETRIES = 3
    pollingTimer = setInterval(async () => {
      try {
        const result = await getResult(tid)
        retries = 0 // 成功后重置
        if (result.status === 'COMPLETED') {
          stopPolling()
          handleResult(result)
        } else if (result.status === 'FAILED') {
          stopPolling()
          status.value = 'failed'
          errorMessage.value = result.errorMessage || '分析失败，请稍后重试'
        }
        // PENDING/PROCESSING 继续轮询
      } catch {
        retries++
        if (retries >= MAX_RETRIES) {
          stopPolling()
          status.value = 'failed'
          errorMessage.value = '查询结果失败，请稍后重试'
        }
        // 否则继续轮询
      }
    }, 2000)
  }

  function stopPolling() {
    if (pollingTimer) {
      clearInterval(pollingTimer)
      pollingTimer = null
    }
  }

  function clearError() {
    status.value = 'idle'
    errorMessage.value = ''
  }

  function handleResult(result: QueryAskResponse | QueryResultResponse) {
    clearSubgraph()
    taskId.value = result.taskId
    answer.value = result.answer
    intent.value = result.intent
    outputFormat.value = result.outputFormat || 'markdown'
    tokenUsage.value = result.tokenUsage
    status.value = 'completed'
    history.value.push({
      question: currentQuestion.value,
      answer: result.answer,
      taskId: result.taskId,
    })
    // 诊断完成，局部刷新历史记录列表（不阻塞 UI）
    loadHistory(1)
  }

  // ── 子图可视化方法 ──

  async function loadSubgraph(taskId: string) {
    subgraphState.value = 'loading'
    try {
      const result = await getPrunedSubgraph(taskId)
      subgraphData.value = result
      subgraphState.value = 'loaded'
    } catch (e: any) {
      subgraphState.value = 'error'
      console.error('[queryStore] loadSubgraph failed:', taskId, e?.message || e)
    }
  }

  function clearSubgraph() {
    subgraphData.value = null
    subgraphState.value = 'idle'
    selectedKpNode.value = null
  }

  // ── 历史记录方法 ──

  async function loadHistory(page?: number, pageSize?: number) {
    historyLoading.value = true
    try {
      const result = await getHistory({
        ...historyFilters.value,
        pageNum: page ?? historyPage.value,
        pageSize: pageSize ?? historyPageSize.value,
      })
      historyRecords.value = result.list
      historyTotal.value = result.total
      historyPage.value = result.pageNum
    } catch {
      // 错误已由 client 拦截器处理
    } finally {
      historyLoading.value = false
    }
  }

  function resetHistoryFilters() {
    historyFilters.value = {}
    historyPage.value = 1
    loadHistory(1)
  }

  function createDownloadLink(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  async function downloadSingleExport(taskId: string) {
    try {
      const blob = await exportSingle(taskId)
      // 检测是否为错误响应（后端返回 JSON 错误而非文件）
      if (blob.type.includes('json') || blob.type.includes('html')) {
        const text = await blob.text()
        if (text.startsWith('{') || text.startsWith('<!DOCTYPE')) {
          console.error('[export] 导出失败，服务器返回错误:', text)
          throw new Error('导出失败，请稍后重试')
        }
      }
      const shortId = taskId.length > 8 ? taskId.substring(0, 8) : taskId
      createDownloadLink(blob, `diagnosis-${shortId}.html`)
    } catch (e: any) {
      console.error('[export] 导出异常:', e)
      throw e
    }
  }

  async function deleteHistoryRecord(taskId: string) {
    await deleteHistory(taskId)
    // 删除后刷新当前列表
    await loadHistory(historyPage.value)
  }

  return {
    currentQuestion, answer, taskId, intent, outputFormat, status, tokenUsage,
    errorMessage, history,
    sendChat, sendAsync, stopPolling, clearError,
    // subgraph visualization
    subgraphData, subgraphState, selectedKpNode,
    loadSubgraph, clearSubgraph,
    // history
    historyRecords, historyTotal, historyLoading, historyPage, historyPageSize,
    historyFilters, loadHistory, resetHistoryFilters,
    downloadSingleExport, deleteHistoryRecord,
  }
})