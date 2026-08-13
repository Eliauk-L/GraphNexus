import client from './client'

export interface AgentChatRequest {
  question: string
  studentNo: string
  subject: string
  dailyMinutes?: number
  days?: number
}

export interface EvidenceRef {
  type: string
  id: string
  summary: string
}

export interface ToolMetrics {
  elapsedMs: number
  resultCount: number
  cacheHit: boolean
}

export interface ToolResult {
  success: boolean
  data: unknown
  evidence: EvidenceRef[]
  warnings: string[]
  metrics: ToolMetrics
  errorCode?: string
}

export interface AgentToolCall {
  round: number
  toolName: string
  argumentsJson: string
  decisionSummary: string
  observation: ToolResult
}

export interface AgentResponse {
  taskId: string
  status: 'COMPLETED' | 'PARTIAL'
  answer: string
  toolsUsed: string[]
  evidence: EvidenceRef[]
  trace: AgentToolCall[]
  warnings: string[]
  fallbackReason?: string
}

export interface AgentTask {
  taskId: string
  userId: string
  status: 'COMPLETED' | 'PARTIAL'
  answerText: string
  fallbackReason?: string
  createTime: string
}

export interface PersistedToolCall {
  id: number
  taskId: string
  roundNo: number
  toolName: string
  argumentsJson: string
  observationJson: string
  decisionSummary: string
  status: 'COMPLETED' | 'FAILED'
  elapsedMs: number
  errorMessage?: string
  createTime: string
}

export const agentApi = {
  chat(payload: AgentChatRequest): Promise<AgentResponse> {
    return client.post('/agent/chat', payload)
  },
  tasks(): Promise<AgentTask[]> {
    return client.get('/agent/tasks')
  },
  trace(taskId: string): Promise<PersistedToolCall[]> {
    return client.get(`/agent/result/${encodeURIComponent(taskId)}/trace`)
  },
}
