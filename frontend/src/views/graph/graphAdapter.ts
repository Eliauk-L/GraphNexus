import type { GraphSubgraphVO, SubgraphResponse } from '@/api/types'

export interface CyElements {
  nodes: CyNode[]
  edges: CyEdge[]
}

interface CyNode {
  data: {
    id: string
    label: string
    nodeType: string
    color: string
    size: number
    [key: string]: unknown
  }
}

interface CyEdge {
  data: {
    id: string
    source: string
    target: string
    type: string
    color: string
    width: number
    lineStyle: 'solid' | 'dashed'
  }
}

const NODE_COLORS: Record<string, string> = {
  KnowledgePoint: '#4A90D9',
  Student: '#52C41A',
  Entity: '#8C8C8C',
  Exam: '#FAAD14',
  KnowledgeCategory: '#722ED1',
  Document: '#BFBFBF',
}

const NODE_SIZES: Record<string, number> = {
  KnowledgePoint: 40,
  Student: 35,
  Exam: 32,
  KnowledgeCategory: 38,
  Document: 32,
  Entity: 28,
}

const EDGE_COLORS: Record<string, string> = {
  PREREQUISITE_OF: '#4A90D9',
  ALIGNED_TO: '#52C41A',
  MASTERS: '#FAAD14',
  TESTED: '#FF7A45',
  CHILD_OF: '#722ED1',
}

export function transformGraphSubgraphVO(vo: GraphSubgraphVO): CyElements {
  const nodeIds = new Set(vo.nodes.map((n) => n.id))

  return {
    nodes: vo.nodes.map((n) => ({
      data: {
        id: n.id,
        label: n.id.substring(0, 8),
        nodeType: n.nodeType,
        color: NODE_COLORS[n.nodeType] ?? '#8C8C8C',
        size: NODE_SIZES[n.nodeType] ?? 28,
      },
    })),
    edges: vo.edges
      .filter((e) => nodeIds.has(e.sourceNodeId) && nodeIds.has(e.targetNodeId))
      .map((e, i) => ({
        data: {
          id: `e-${i}`,
          source: e.sourceNodeId,
          target: e.targetNodeId,
          type: e.edgeType,
          color: EDGE_COLORS[e.edgeType] ?? '#8C8C8C',
          width: 1.5,
          lineStyle: ['BELONGS_TO', 'CHILD_OF', 'REFERENCES'].includes(e.edgeType) ? 'dashed' as const : 'solid' as const,
        },
      })),
  }
}

export function transformSubgraphResponse(res: SubgraphResponse): CyElements {
  const nodeIds = new Set(res.nodes.map((n) => n.id))

  return {
    nodes: res.nodes.map((n) => ({
      data: {
        id: n.id,
        label: (n.properties.name as string) ?? (n.properties.label as string) ?? n.id.substring(0, 8),
        nodeType: n.nodeType,
        color: NODE_COLORS[n.nodeType] ?? '#8C8C8C',
        size: NODE_SIZES[n.nodeType] ?? 28,
        ...n.properties,
      },
    })),
    edges: res.edges
      .filter((e) => nodeIds.has(e.sourceNodeId) && nodeIds.has(e.targetNodeId))
      .map((e, i) => ({
        data: {
          id: `e-${i}`,
          source: e.sourceNodeId,
          target: e.targetNodeId,
          type: e.edgeType,
          color: EDGE_COLORS[e.edgeType] ?? '#8C8C8C',
          width: 1 + Math.min(e.weight, 3),
          lineStyle: ['BELONGS_TO', 'CHILD_OF', 'REFERENCES'].includes(e.edgeType) ? 'dashed' as const : 'solid' as const,
        },
      })),
  }
}