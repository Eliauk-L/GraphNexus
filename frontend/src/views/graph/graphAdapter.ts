import type { GraphSubgraphVO, SubgraphResponse, MetricResultVO } from '@/api/types'
import {
  NODE_COLORS,
  NODE_SIZES,
  EDGE_COLORS,
  EDGE_LINE_STYLES,
  EDGE_WIDTHS,
  DEFAULT_NODE_COLOR,
  DEFAULT_NODE_SIZE,
  DEFAULT_EDGE_COLOR,
  DEFAULT_EDGE_WIDTH,
  METRIC_SIZE_MIN,
  METRIC_SIZE_MAX,
  METRIC_SIZE_DEFAULT,
  PAGERANK_COLORS,
  percentileIndex,
  linearMap,
} from './constants'

// ── G6 v5 GraphData 类型 ──

export interface G6GraphData {
  nodes: G6GraphNode[]
  edges: G6GraphEdge[]
}

export interface G6GraphNode {
  id: string
  data: {
    label: string
    nodeType: string
    color: string
    size: number
    [key: string]: unknown
  }
}

export interface G6GraphEdge {
  id: string
  source: string
  target: string
  data: {
    type: string
    color: string
    width: number
    lineStyle: 'solid' | 'dashed'
    [key: string]: unknown
  }
}

// ── 统一转换入口 ──

/**
 * 将后端图谱数据转换为 G6 v5 GraphData 格式。
 * 判别方式：SubgraphResponse 有 taskId/pruningMeta 字段，GraphSubgraphVO 没有。
 */
export function toGraphData(
  input: GraphSubgraphVO | SubgraphResponse,
): G6GraphData {
  if ('taskId' in input) {
    return transformPruningSubgraph(input as SubgraphResponse)
  }
  return transformDocumentSubgraph(input as GraphSubgraphVO)
}

// ── 文档子图转换（GraphSubgraphVO · 轻量）──

/** 文档子图展示的节点类型 */
const DOCUMENT_SUBGRAPH_NODE_TYPES = new Set(['Document', 'Entity', 'KnowledgePoint'])

function transformDocumentSubgraph(vo: GraphSubgraphVO): G6GraphData {
  // 优先展示 Entity + KnowledgePoint，若过滤后为空则展示全部
  let filteredNodes = vo.nodes.filter((n) => DOCUMENT_SUBGRAPH_NODE_TYPES.has(n.nodeType))
  if (filteredNodes.length === 0) {
    filteredNodes = vo.nodes
  }
  const nodeIds = new Set(filteredNodes.map((n) => n.id))

  return {
    nodes: filteredNodes.map((n) => ({
      id: n.id,
      data: {
        // 先铺 Neo4j 属性，再覆盖 G6 渲染字段
        ...n.properties,
        label: n.name ?? n.nodeType,
        nodeType: n.nodeType,
        color: NODE_COLORS[n.nodeType] ?? DEFAULT_NODE_COLOR,
        size: NODE_SIZES[n.nodeType] ?? DEFAULT_NODE_SIZE,
      },
    })),
    edges: vo.edges
      .filter((e) => nodeIds.has(e.sourceNodeId) && nodeIds.has(e.targetNodeId))
      .map((e, i) => ({
        id: `e-${i}`,
        source: e.sourceNodeId,
        target: e.targetNodeId,
        data: {
          type: e.edgeType,
          color: EDGE_COLORS[e.edgeType] ?? DEFAULT_EDGE_COLOR,
          width: EDGE_WIDTHS[e.edgeType] ?? DEFAULT_EDGE_WIDTH,
          lineStyle: EDGE_LINE_STYLES[e.edgeType] ?? 'solid' as const,
        },
      })),
  }
}

// ── 剪枝子图转换（SubgraphResponse · 含 properties + weight + PruningMeta）──

function transformPruningSubgraph(res: SubgraphResponse): G6GraphData {
  const nodeIds = new Set(res.nodes.map((n) => n.id))

  return {
    nodes: res.nodes.map((n) => ({
      id: n.id,
      data: {
        label: n.label ?? (n.properties?.name as string) ?? (n.properties?.label as string) ?? n.id.substring(0, 8),
        nodeType: n.nodeType,
        color: NODE_COLORS[n.nodeType] ?? DEFAULT_NODE_COLOR,
        size: NODE_SIZES[n.nodeType] ?? DEFAULT_NODE_SIZE,
        ...n.properties,
      },
    })),
    edges: res.edges
      .filter((e) => nodeIds.has(e.sourceNodeId) && nodeIds.has(e.targetNodeId))
      .map((e, i) => ({
        id: `e-${i}`,
        source: e.sourceNodeId,
        target: e.targetNodeId,
        data: {
          type: e.edgeType,
          color: EDGE_COLORS[e.edgeType] ?? DEFAULT_EDGE_COLOR,
          width: DEFAULT_EDGE_WIDTH + Math.min(e.weight, 3),
          lineStyle: EDGE_LINE_STYLES[e.edgeType] ?? 'solid' as const,
          weight: e.weight,
        },
      })),
  }
}

// ── 度量映射 ──

/**
 * 将度量数据应用到 G6 GraphData，更新 KnowledgePoint 节点的 size 和 color。
 *
 * - size: 基于总度数线性映射到 [METRIC_SIZE_MIN, METRIC_SIZE_MAX]
 * - color: 若 pagerankData 非空，按 PageRank 百分位映射 PAGERANK_COLORS 色阶
 */
export function applyMetrics(
  graphData: G6GraphData,
  degreeData: MetricResultVO[],
  pagerankData?: MetricResultVO[],
): G6GraphData {
  const degreeMap = new Map<string, { inDegree: number; outDegree: number; totalDegree: number }>()
  for (const d of degreeData) {
    if (d.nodeType !== 'KnowledgePoint') continue
    const entry = degreeMap.get(d.nodeId) ?? { inDegree: 0, outDegree: 0, totalDegree: 0 }
    if (d.metricName === 'inDegree') entry.inDegree = d.metricValue
    else if (d.metricName === 'outDegree') entry.outDegree = d.metricValue
    entry.totalDegree = entry.inDegree + entry.outDegree
    degreeMap.set(d.nodeId, entry)
  }

  const degrees = [...degreeMap.values()].map((e) => e.totalDegree)
  const minDegree = degrees.length > 0 ? Math.min(...degrees) : 0
  const sortedDeg = [...degrees].sort((a, b) => a - b)
  const p95Idx = Math.floor(sortedDeg.length * 0.95)
  const maxDegree = sortedDeg.length > 0 ? sortedDeg[Math.min(p95Idx, sortedDeg.length - 1)] : 1

  let pagerankMap: Map<string, number> | null = null
  let allScores: number[] = []
  if (pagerankData && pagerankData.length > 0) {
    pagerankMap = new Map<string, number>()
    for (const p of pagerankData) {
      if (p.nodeType === 'KnowledgePoint') {
        pagerankMap.set(p.nodeId, p.metricValue)
        allScores.push(p.metricValue)
      }
    }
  }

  const updatedNodes = graphData.nodes.map((n) => {
    if (n.data.nodeType !== 'KnowledgePoint') return n
    const deg = degreeMap.get(n.id)
    const pr = pagerankMap?.get(n.id)
    const size = deg
      ? linearMap(deg.totalDegree, minDegree, maxDegree, METRIC_SIZE_MIN, METRIC_SIZE_MAX)
      : METRIC_SIZE_DEFAULT
    const color = pr != null && allScores.length > 0
      ? PAGERANK_COLORS[percentileIndex(pr, allScores, PAGERANK_COLORS.length)]
      : n.data.color

    return { ...n, data: { ...n.data, size, color } }
  })

  return { ...graphData, nodes: updatedNodes }
}