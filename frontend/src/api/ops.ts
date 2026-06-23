import client from './client'

/** 运营摘要 — 使用量 */
export interface OpsUsageSummary {
  activeUsers: number
  loginCount: number
  documentUploadCount: number
  documentProcessCount: number
  qaAskCount: number
  operationDistribution: Array<{ type: string; count: number }>
}

/** 运营摘要 — 文档处理量 */
export interface OpsDocumentSummary {
  total: number
  byStatus: Record<string, number>
  bySubject: Record<string, number>
}

/** 运营摘要 — 图谱分布 */
export interface OpsGraphDistribution {
  nodesByType: Record<string, number>
  edgesByType: Record<string, number>
  bySubject: Record<string, { nodes: Record<string, number>; edges: Record<string, number> }>
}

/** 运营摘要完整响应 */
export interface OpsSummaryResponse {
  usage: OpsUsageSummary
  documents: OpsDocumentSummary
  graph: OpsGraphDistribution
}

/** 历史趋势数据点 */
export interface TrendDataPoint {
  date: string
  value: number
}

/** 运营趋势响应 */
export interface OpsTrendResponse {
  metric: string
  granularity: string
  dataPoints: TrendDataPoint[]
}

/** 运营统计 API */
export const opsApi = {
  getSummary(): Promise<OpsSummaryResponse> {
    return client.get('/ops/stats/summary')
  },

  getTrend(metric: string, granularity: string, range: number): Promise<OpsTrendResponse> {
    return client.get('/ops/stats/trend', { params: { metric, granularity, range } })
  },

  getSubjects(): Promise<string[]> {
    return client.get('/ops/stats/subjects')
  },
}