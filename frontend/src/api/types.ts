// ============================================
// GraphNexus API TypeScript 类型定义
// 来源：CHANGE.md 后端 API 清单 + CONTEXT.md DTO 定义
// ============================================

// ── 通用响应 ──

/** 统一 API 响应体 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId: string
  timestamp: number
}

/** 分页响应体 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/** 错误响应体 */
export interface ErrorResponse {
  errorCode: string
  errorMessage: string
  userTip: string
  traceId: string
  timestamp: number
}

// ── 文档管理 ──

export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface DocumentVO {
  documentId: number
  documentNo: string
  name: string
  subject: string
  fileSize: number
  minioPath: string
  pageCount: number
  status: DocumentStatus
  createTime: string
  updateTime: string
}

export interface ParseResultVO {
  documentId: number
  textContent: string
  pageCount: number
}

export interface UpdateDocumentRequest {
  name: string
}

export interface DeleteResultVO {
  deletedMySqlRecords: number
  deletedMinioPath: string
  deletedNeo4jEdges: number
}

// ── 成绩管理 ──

export interface GradeRecordVO {
  id: number
  studentNo: string
  name: string
  className: string
  examNo: string
  examName: string
  subject: string
  totalScore: number
  classRank: number
  scoreDetails: string
}

export interface GradeUploadResultVO {
  examNo: string
  examName: string
  examDate: string
  subject: string
  studentCount: number
  questionCount: number
  knowledgePoints: string[]
  minioPath: string
  csvMd5: string
}

// ── 知识图谱 ──

export interface ExtractionResultVO {
  documentId: number
  entityCount: number
  knowledgePointCount: number
  categoryCount: number
  edgeCount: number
}

export interface GraphNodeVO {
  id: string
  nodeType: string
  documentId: string
  createdAt: string
}

export interface GraphEdgeVO {
  sourceNodeId: string
  targetNodeId: string
  edgeType: string
  createdAt: string
}

export interface GraphSubgraphVO {
  nodes: GraphNodeVO[]
  edges: GraphEdgeVO[]
}

// ── 融合管理 ──

export interface FusionExecuteVO {
  fusionLogId: number
  mergedKpGroupCount: number
  mastersEdgeCount: number
}

export interface FusionStatusVO {
  fusionLogId: number
  triggerType: 'MANUAL' | 'AUTO_INCREMENTAL'
  status: string
  executedAt: string
  mergedKpGroupCount: number
  mastersEdgeCount: number
  rolledBack: boolean
  fusionDetailJson: string
  mastersSnapshotJson: string
}

export interface FusionRollbackVO {
  fusionLogId: number
  restoredKpCount: number
  restoredEdgeCount: number
}

// ── 图指标 ──

export interface MetricResultVO {
  nodeId: string
  nodeType: string
  metricName: 'PageRank' | 'inDegree' | 'outDegree'
  metricValue: number
}

// ── 智能问答 ──

export interface QueryAskRequest {
  question: string
  studentName?: string
  studentNo?: string
  subject: string
}

export interface QueryChatRequest {
  question: string
}

export interface TokenUsageVO {
  prunedNodes: number
  prunedEdges: number
  estimatedTokens: number
}

export interface QueryAskResponse {
  taskId: string
  question: string
  intent: string
  answer: string
  status: string
  tokenUsage: TokenUsageVO | null
}

export interface QueryAsyncResponse {
  taskId: string
  status: string
  createdAt: string
}

export type QueryTaskStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface QueryResultResponse {
  taskId: string
  status: QueryTaskStatus
  question: string
  intent: string
  answer: string
  tokenUsage: TokenUsageVO | null
  errorMessage: string
  createdAt: string
  updatedAt: string
}

// ── 图分析 ──

export interface SubgraphNodeVO {
  id: string
  nodeType: string
  properties: Record<string, unknown>
}

export interface SubgraphEdgeVO {
  sourceNodeId: string
  targetNodeId: string
  edgeType: string
  weight: number
}

export interface PruningMetaVO {
  strategy: string
  mastersAvailable: boolean
  weakThreshold: number
  maxHops: number
  totalNodes: number
  totalEdges: number
  truncated: boolean
  truncatedNodeNames: string[]
}

export interface SubgraphResponse {
  taskId: string
  status: string
  nodes: SubgraphNodeVO[]
  edges: SubgraphEdgeVO[]
  pruningMeta: PruningMetaVO
}