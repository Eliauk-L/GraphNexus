import client from './client'
import type { ExtractionResultVO, GraphSubgraphVO, FusionExecuteVO, FusionStatusVO, FusionRollbackVO, MetricResultVO } from './types'

/** 触发文档知识图谱抽取 */
export function extractGraph(documentId: number): Promise<ExtractionResultVO> {
  return client.post(`/graph/extract/${documentId}`)
}

/** 查询文档子图 */
export function getDocumentSubgraph(documentId: number): Promise<GraphSubgraphVO> {
  return client.get(`/graph/document/${documentId}`)
}

/** 手动全量融合 */
export function executeFusion(): Promise<FusionExecuteVO> {
  return client.post('/graph/fusion/execute')
}

/** 查询融合状态 */
export function getFusionStatus(): Promise<FusionStatusVO | null> {
  return client.get('/graph/fusion/status')
}

/** 回滚融合 */
export function rollbackFusion(fusionLogId: number): Promise<FusionRollbackVO> {
  return client.post(`/graph/fusion/rollback/${fusionLogId}`)
}

/** 查询 PageRank */
export function queryPageRank(nodeTypes?: string[], edgeTypes?: string[]): Promise<MetricResultVO[]> {
  return client.get('/graph/metrics/pagerank', {
    params: { nodeTypes: nodeTypes?.join(','), edgeTypes: edgeTypes?.join(',') },
    paramsSerializer: { indexes: null },
  })
}

/** 查询度中心性 */
export function queryDegree(nodeTypes?: string[], edgeTypes?: string[]): Promise<MetricResultVO[]> {
  return client.get('/graph/metrics/degree', {
    params: { nodeTypes: nodeTypes?.join(','), edgeTypes: edgeTypes?.join(',') },
    paramsSerializer: { indexes: null },
  })
}