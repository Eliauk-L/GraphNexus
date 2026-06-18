import client from './client'
import type { SubgraphResponse } from './types'

/** 查询剪枝子图 */
export function getPrunedSubgraph(taskId: string): Promise<SubgraphResponse> {
  return client.get(`/analysis/subgraph/${taskId}`)
}