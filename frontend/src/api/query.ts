import client from './client'
import type { QueryAskRequest, QueryChatRequest, QueryAskResponse, QueryAsyncResponse, QueryResultResponse, HistoryQueryParams, HistoryRecordVO, PageResult } from './types'
import axios from 'axios'

/** 同步问答 */
export function askSync(req: QueryAskRequest): Promise<QueryAskResponse> {
  return client.post('/query/ask', req)
}

/** 异步问答 */
export function askAsync(req: QueryAskRequest): Promise<QueryAsyncResponse> {
  return client.post('/query/ask-async', req)
}

/** 智能对话（仅需自然语言问题），不设超时，等待大模型完整返回 */
export function chat(req: QueryChatRequest): Promise<QueryAskResponse> {
  return client.post('/query/chat', req, { timeout: 0 })
}

/** 查询异步结果 */
export function getResult(taskId: string): Promise<QueryResultResponse> {
  return client.get(`/query/result/${taskId}`)
}

/** 历史诊断记录分页查询 */
export function getHistory(params: HistoryQueryParams): Promise<PageResult<HistoryRecordVO>> {
  const filtered: Record<string, string | number> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') {
      filtered[k] = v
    }
  }
  return client.get('/query/history', { params: filtered })
}

/** 导出单条诊断报告（返回 Blob） */
export async function exportSingle(taskId: string): Promise<Blob> {
  const resp = await axios.get(`/api/v1/query/history/${taskId}/export`, {
    responseType: 'blob',
  })
  return resp.data as Blob
}

/** 批量导出历史记录为 Excel（返回 Blob） */
export async function exportBatch(params: HistoryQueryParams): Promise<Blob> {
  const filtered: Record<string, string | number> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') {
      filtered[k] = v
    }
  }
  const resp = await axios.get('/api/v1/query/history/export', {
    params: filtered,
    responseType: 'blob',
  })
  return resp.data as Blob
}