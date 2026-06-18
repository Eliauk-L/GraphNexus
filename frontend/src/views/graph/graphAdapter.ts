import type { GraphSubgraphVO, SubgraphResponse } from '@/api/types'

// G6 v5 数据格式
export interface G6GraphData {
  nodes: Array<{ id: string; data: Record<string, unknown> }>
  edges: Array<{ source: string; target: string; data: Record<string, unknown> }>
}

// Neo4j 风格颜色映射
const NODE_COLORS: Record<string, string> = {
  KnowledgePoint: '#4A90D9',
  Student: '#52C41A',
  Entity: '#8C8C8C',
  Exam: '#FAAD14',
  KnowledgeCategory: '#722ED1',
  Document: '#BFBFBF',
}

const NODE_SIZES: Record<string, number> = {
  KnowledgePoint: 32,
  Student: 28,
  Entity: 24,
  Exam: 26,
  KnowledgeCategory: 30,
  Document: 26,
}

const EDGE_COLORS: Record<string, string> = {
  PREREQUISITE_OF: '#4A90D9',
  ALIGNED_TO: '#52C41A',
  MASTERS: '#FAAD14',
  BELONGS_TO: '#8C8C8C',
  CHILD_OF: '#722ED1',
  TESTED: '#FF7A45',
  ATTENDED: '#8C8C8C',
  EXTRACTS: '#8C8C8C',
  DERIVES: '#8C8C8C',
  CONTAINS: '#8C8C8C',
  REFERENCES: '#8C8C8C',
}

export function transformGraphSubgraphVO(vo: GraphSubgraphVO): G6GraphData {
  return {
    nodes: vo.nodes.map((n) => ({
      id: n.id,
      data: {
        label: n.id.substring(0, 8),
        nodeType: n.nodeType,
        fill: NODE_COLORS[n.nodeType] ?? '#8C8C8C',
        size: NODE_SIZES[n.nodeType] ?? 26,
        documentId: n.documentId,
      },
    })),
    edges: vo.edges.map((e) => ({
      source: e.sourceNodeId,
      target: e.targetNodeId,
      data: {
        type: e.edgeType,
        stroke: EDGE_COLORS[e.edgeType] ?? '#8C8C8C',
        lineWidth: 1.5,
        lineDash: ['BELONGS_TO', 'CHILD_OF', 'REFERENCES'].includes(e.edgeType) ? [4, 4] : undefined,
      },
    })),
  }
}

export function transformSubgraphResponse(res: SubgraphResponse): G6GraphData {
  return {
    nodes: res.nodes.map((n) => ({
      id: n.id,
      data: {
        label: (n.properties.name as string) ?? (n.properties.label as string) ?? n.id.substring(0, 8),
        nodeType: n.nodeType,
        fill: NODE_COLORS[n.nodeType] ?? '#8C8C8C',
        size: NODE_SIZES[n.nodeType] ?? 26,
        ...n.properties,
      },
    })),
    edges: res.edges.map((e) => ({
      source: e.sourceNodeId,
      target: e.targetNodeId,
      data: {
        type: e.edgeType,
        stroke: EDGE_COLORS[e.edgeType] ?? '#8C8C8C',
        lineWidth: 1 + Math.min(e.weight, 3),
        lineDash: ['BELONGS_TO', 'CHILD_OF', 'REFERENCES'].includes(e.edgeType) ? [4, 4] : undefined,
      },
    })),
  }
}