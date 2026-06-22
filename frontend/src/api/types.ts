// ============================================
// GraphNexus API TypeScript 类型定义
// 来源：后端 Controller + DTO/VO（2026-06-18 审查）
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

// ── 文件管理（FileController）──

export type FileStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'PARSED'
  | 'EXTRACTING'
  | 'EXTRACTED'
  | 'FUSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'DELETING'

export interface TextbookVO {
  documentId: number
  documentNo: string
  name: string
  subject: string
  fileSize: number
  filePath: string
  pageCount: number
  status: FileStatus
  fileType: string
  failReason?: string
  createTime: string
}

export interface TextbookParseResultVO {
  documentId: number
  textContent: string
  pageCount: number
  metadata?: Record<string, string>
}

export interface DeleteResultVO {
  deletedMySqlRecords: number
  deletedMinioPath: string
  deletedNeo4jEdges: number
}

// ── 成绩管理（GradeController）──

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
  filePath: string
  csvMd5: string
}

/** 考试汇总（管理考试弹窗用） */
export interface ExamSummaryVO {
  examNo: string
  examName: string
  examDate: string
  subject: string
  studentCount: number
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
  name?: string
  description?: string
  properties?: Record<string, unknown>
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
  outputFormat?: string  // 'html-svg' | 'markdown'
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
  outputFormat?: string  // 'html-svg' | 'markdown'
  tokenUsage: TokenUsageVO | null
  errorMessage: string
  createdAt: string
  updatedAt: string
}

// ── 图分析 ──

export interface SubgraphNodeVO {
  id: string
  nodeType: string
  label?: string  // 显示名称（从原始节点属性推导）
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

// ── 历史诊断记录 ──

/** 历史记录查询参数 */
export interface HistoryQueryParams {
  studentName?: string
  studentNo?: string
  subject?: string
  status?: string
  startDate?: string   // ISO date: yyyy-MM-dd
  endDate?: string     // ISO date: yyyy-MM-dd
  pageNum?: number
  pageSize?: number
}

/** 历史诊断记录列表项（不含 answer 正文） */
export interface HistoryRecordVO {
  taskId: string
  question: string
  studentName: string
  studentNo: string
  subject: string
  status: string
  intent: string
  tokenUsage: TokenUsageVO | null
  elapsedMs: number
  errorMessage: string | null
  createTime: string
}