import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chat, askAsync, getResult } from '@/api/query'
import type { QueryAskResponse, QueryResultResponse, TokenUsageVO } from '@/api/types'

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

  async function sendChat(question: string) {
    currentQuestion.value = question
    status.value = 'pending'
    errorMessage.value = ''

    try {
      const result = await chat({ question })
      handleResult(result)
    } catch (e) {
      status.value = 'failed'
      errorMessage.value = '问答请求失败'
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
    pollingTimer = setInterval(async () => {
      try {
        const result = await getResult(tid)
        if (result.status === 'COMPLETED') {
          stopPolling()
          handleResult(result)
        } else if (result.status === 'FAILED') {
          stopPolling()
          status.value = 'failed'
          errorMessage.value = result.errorMessage
        }
      } catch {
        stopPolling()
        status.value = 'failed'
        errorMessage.value = '查询结果失败'
      }
    }, 1500)
  }

  function stopPolling() {
    if (pollingTimer) {
      clearInterval(pollingTimer)
      pollingTimer = null
    }
  }

  function handleResult(result: QueryAskResponse | QueryResultResponse) {
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
  }

  return {
    currentQuestion, answer, taskId, intent, outputFormat, status, tokenUsage,
    errorMessage, history,
    sendChat, sendAsync, stopPolling,
  }
})