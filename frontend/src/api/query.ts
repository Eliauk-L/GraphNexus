import client from './client'
import type { QueryAskRequest, QueryChatRequest, QueryAskResponse, QueryAsyncResponse, QueryResultResponse } from './types'

/** 同步问答 */
export function askSync(req: QueryAskRequest): Promise<QueryAskResponse> {
  return client.post('/query/ask', req)
}

/** 异步问答 */
export function askAsync(req: QueryAskRequest): Promise<QueryAsyncResponse> {
  return client.post('/query/ask-async', req)
}

/** 智能对话（仅需自然语言问题） */
export function chat(req: QueryChatRequest): Promise<QueryAskResponse> {
  return client.post('/query/chat', req)
}

/** 查询异步结果 */
export function getResult(taskId: string): Promise<QueryResultResponse> {
  return client.get(`/query/result/${taskId}`)
}