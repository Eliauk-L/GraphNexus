import client from './client'

export interface MasteryView {
  knowledgePointId: string
  knowledgePointName: string
  weight: number
  confidence: number
  sampleCount: number
  lastExamNo?: string
  lastUpdatedAt?: string
}

export interface MasteryHistoryView {
  eventId: string
  examNo: string
  scoreRate: number
  oldWeight?: number
  newWeight: number
  alpha: number
  sampleCount: number
  confidence: number
  evidenceJson: string
  occurredAt: string
}

export const masteryApi = {
  current(studentNo: string, subject: string): Promise<MasteryView[]> {
    return client.get(`/mastery/${encodeURIComponent(studentNo)}`, { params: { subject } })
  },
  history(studentNo: string, knowledgePointId: string): Promise<MasteryHistoryView[]> {
    return client.get(`/mastery/${encodeURIComponent(studentNo)}/${encodeURIComponent(knowledgePointId)}/history`)
  },
}
