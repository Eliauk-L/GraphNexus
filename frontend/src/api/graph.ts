import client from './client'
import type { ExtractionResultVO, GraphSubgraphVO, MetricResultVO } from './types'

/** 触发文档知识图谱抽取 */
export function extractGraph(documentId: number): Promise<ExtractionResultVO> {
  return client.post(`/graph/extract/${documentId}`)
}

/** 查询文档子图 */
export function getDocumentSubgraph(documentId: number): Promise<GraphSubgraphVO> {
  return client.get(`/graph/construction/document/${documentId}`)
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