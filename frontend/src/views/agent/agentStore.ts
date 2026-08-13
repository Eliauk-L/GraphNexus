import { defineStore } from 'pinia'
import { ref } from 'vue'
import { agentApi, type AgentChatRequest, type AgentResponse, type AgentTask, type PersistedToolCall } from '@/api/agent'
import { masteryApi, type MasteryHistoryView, type MasteryView } from '@/api/mastery'

export const useAgentStore = defineStore('teachingAgent', () => {
  const loading = ref(false)
  const masteryLoading = ref(false)
  const response = ref<AgentResponse | null>(null)
  const tasks = ref<AgentTask[]>([])
  const persistedTrace = ref<PersistedToolCall[]>([])
  const mastery = ref<MasteryView[]>([])
  const masteryHistory = ref<MasteryHistoryView[]>([])
  const selectedKnowledgePoint = ref<MasteryView | null>(null)

  async function ask(payload: AgentChatRequest) {
    loading.value = true
    try {
      response.value = await agentApi.chat(payload)
      await loadTasks()
      return response.value
    } finally {
      loading.value = false
    }
  }

  async function loadTasks() {
    tasks.value = await agentApi.tasks()
  }

  async function loadTrace(taskId: string) {
    persistedTrace.value = await agentApi.trace(taskId)
  }

  async function loadMastery(studentNo: string, subject: string) {
    masteryLoading.value = true
    try {
      mastery.value = await masteryApi.current(studentNo, subject)
        .then(items => [...items].sort((a, b) => a.weight - b.weight || a.knowledgePointName.localeCompare(b.knowledgePointName)))
    } finally {
      masteryLoading.value = false
    }
  }

  async function loadHistory(studentNo: string, item: MasteryView) {
    selectedKnowledgePoint.value = item
    masteryHistory.value = await masteryApi.history(studentNo, item.knowledgePointId)
  }

  return {
    loading, masteryLoading, response, tasks, persistedTrace, mastery,
    masteryHistory, selectedKnowledgePoint, ask, loadTasks, loadTrace, loadMastery, loadHistory,
  }
})
