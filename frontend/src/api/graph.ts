import client from './client'
import type { ExtractionResultVO, GraphSubgraphVO, MetricResultVO } from './types'

/** 触发文档知识图谱抽取 */
export function extractGraph(documentId: number): Promise<ExtractionResultVO> {
  return client.post(`/graph/construction/extract/${documentId}`)
}

/** 查询文档子图 */
export function getDocumentSubgraph(documentId: number): Promise<GraphSubgraphVO> {
  return client.get(`/graph/construction/document/${documentId}`)
}

/** 查询 PageRank（支持 subject 或 documentId 过滤） */
export function queryPageRank(
  nodeTypes?: string[],
  edgeTypes?: string[],
  subject?: string,
  documentId?: string,
): Promise<MetricResultVO[]> {
  return client.get('/graph/metrics/pagerank', {
    params: { nodeTypes: nodeTypes?.join(','), edgeTypes: edgeTypes?.join(','), subject, documentId },
    paramsSerializer: { indexes: null },
  })
}

/** 查询度中心性（支持 subject 或 documentId 过滤） */
export function queryDegree(
  nodeTypes?: string[],
  edgeTypes?: string[],
  subject?: string,
  documentId?: string,
): Promise<MetricResultVO[]> {
  return client.get('/graph/metrics/degree', {
    params: { nodeTypes: nodeTypes?.join(','), edgeTypes: edgeTypes?.join(','), subject, documentId },
    paramsSerializer: { indexes: null },
  })
}

/** 学科列表 */
export function listSubjects(): Promise<string[]> {
  return client.get('/graph/construction/subjects')
}

/** 学科全景图（数据量大，单独设长超时） */
export function getSubjectGraph(subjectName: string): Promise<GraphSubgraphVO> {
  return client.get(`/graph/construction/subject/${encodeURIComponent(subjectName)}`, {
    timeout: 30000,
  })
}

/** 全量图谱 — Neo4j 所有节点和边 */
export function getFullGraph(): Promise<GraphSubgraphVO> {
  return client.get('/graph/construction/full', { timeout: 60000 })
}

/** 查询考试频次 */
export function queryExamFrequency(subject?: string, documentId?: string): Promise<MetricResultVO[]> {
  return client.get('/graph/metrics/exam-frequency', {
    params: { subject, documentId },
    paramsSerializer: { indexes: null },
  })
}