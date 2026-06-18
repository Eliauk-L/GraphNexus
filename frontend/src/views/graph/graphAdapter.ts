import type { GraphSubgraphVO, SubgraphResponse, GraphNodeVO, GraphEdgeVO, SubgraphNodeVO, SubgraphEdgeVO } from '@/api/types'

export interface G6GraphData {
  nodes: G6Node[]
  edges: G6Edge[]
}

export interface G6Node {
  id: string
  label: string
  nodeType: string
  style: Record<string, unknown>
  data: Record<string, unknown>
}

export interface G6Edge {
  source: string
  target: string
  type: string
  weight: number
  style: Record<string, unknown>
}

const NODE_COLORS: Record<string, string> = {
  KnowledgePoint: 'oklch(0.55 0.18 250)',
  Student: 'oklch(0.55 0.15 145)',
  Entity: 'oklch(0.55 0.02 95)',
  Exam: 'oklch(0.65 0.15 85)',
  KnowledgeCategory: 'oklch(0.50 0.05 280)',
  Document: 'oklch(0.45 0.02 95)',
}

const NODE_SIZES: Record<string, number> = {
  KnowledgePoint: 36,
  Student: 32,
  Entity: 28,
  Exam: 30,
  KnowledgeCategory: 34,
  Document: 30,
}

const EDGE_COLORS: Record<string, string> = {
  PREREQUISITE_OF: 'oklch(0.55 0.18 250)',
  ALIGNED_TO: 'oklch(0.55 0.15 145)',
  MASTERS: 'oklch(0.65 0.15 85)',
  BELONGS_TO: 'oklch(0.50 0.02 95)',
  CHILD_OF: 'oklch(0.50 0.05 280)',
  TESTED: 'oklch(0.55 0.10 40)',
  ATTENDED: 'oklch(0.45 0.02 95)',
  EXTRACTS: 'oklch(0.50 0.02 95)',
  DERIVES: 'oklch(0.50 0.02 95)',
  CONTAINS: 'oklch(0.50 0.02 95)',
  REFERENCES: 'oklch(0.50 0.02 95)',
}

/** 文档子图 VO → G6 数据 */
export function transformGraphSubgraphVO(vo: GraphSubgraphVO): G6GraphData {
  return {
    nodes: vo.nodes.map((n) => toG6Node(n.id, n.nodeType, { documentId: n.documentId })),
    edges: vo.edges.map((e) => toG6Edge(e.sourceNodeId, e.targetNodeId, e.edgeType)),
  }
}

/** 剪枝子图 Response → G6 数据 */
export function transformSubgraphResponse(res: SubgraphResponse): G6GraphData {
  return {
    nodes: res.nodes.map((n) => toG6Node(n.id, n.nodeType, n.properties)),
    edges: res.edges.map((e) => toG6Edge(e.sourceNodeId, e.targetNodeId, e.edgeType, e.weight)),
  }
}

function toG6Node(id: string, nodeType: string, props: Record<string, unknown>): G6Node {
  return {
    id,
    label: (props.name as string) ?? (props.label as string) ?? `${nodeType}-${id.substring(0, 6)}`,
    nodeType,
    style: {
      fill: NODE_COLORS[nodeType] ?? 'oklch(0.50 0.02 95)',
      size: NODE_SIZES[nodeType] ?? 28,
    },
    data: props,
  }
}

function toG6Edge(source: string, target: string, type: string, weight = 1.0): G6Edge {
  const isDashed = ['BELONGS_TO', 'CHILD_OF', 'REFERENCES'].includes(type)
  return {
    source,
    target,
    type,
    weight,
    style: {
      stroke: EDGE_COLORS[type] ?? 'oklch(0.60 0.01 95)',
      lineWidth: 1 + Math.min(weight, 3),
      lineDash: isDashed ? [4, 4] : undefined,
    },
  }
}