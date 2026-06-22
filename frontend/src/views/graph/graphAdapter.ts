import type { GraphSubgraphVO, SubgraphResponse } from '@/api/types'
import {
  NODE_COLORS,
  NODE_SIZES,
  EDGE_COLORS,
  EDGE_LINE_STYLES,
  DEFAULT_NODE_COLOR,
  DEFAULT_NODE_SIZE,
  DEFAULT_EDGE_COLOR,
  DEFAULT_EDGE_WIDTH,
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
  // 只展示 Document / Entity / KnowledgePoint，过滤其余
  const filteredNodes = vo.nodes.filter((n) => DOCUMENT_SUBGRAPH_NODE_TYPES.has(n.nodeType))
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
          width: DEFAULT_EDGE_WIDTH,
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
        label: (n.properties.name as string) ?? (n.properties.label as string) ?? n.id.substring(0, 8),
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