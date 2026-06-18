import type { GraphSubgraphVO, SubgraphResponse } from '@/api/types'
import type { Node, Relationship } from '@neo4j-nvl/base'

export interface NvlGraphData {
  nodes: Node[]
  relationships: Relationship[]
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

/** 文档子图 VO → NVL 数据 */
export function transformGraphSubgraphVO(vo: GraphSubgraphVO): NvlGraphData {
  return {
    nodes: vo.nodes.map((n) => toNvlNode(n.id, n.nodeType, { documentId: n.documentId })),
    relationships: vo.edges.map((e, i) =>
      toNvlRel(`ge-${i}`, e.sourceNodeId, e.targetNodeId, e.edgeType),
    ),
  }
}

/** 剪枝子图 Response → NVL 数据 */
export function transformSubgraphResponse(res: SubgraphResponse): NvlGraphData {
  return {
    nodes: res.nodes.map((n) => toNvlNode(n.id, n.nodeType, n.properties)),
    relationships: res.edges.map((e, i) =>
      toNvlRel(`se-${i}`, e.sourceNodeId, e.targetNodeId, e.edgeType, e.weight),
    ),
  }
}

function nodeLabel(nodeType: string, props: Record<string, unknown>): string {
  const name = (props.name as string) ?? (props.label as string)
  return name ?? nodeType
}

function toNvlNode(id: string, nodeType: string, props: Record<string, unknown>): Node {
  return {
    id,
    captions: [{ value: nodeLabel(nodeType, props) }],
    color: NODE_COLORS[nodeType] ?? '#8C8C8C',
    size: NODE_SIZES[nodeType] ?? 26,
  }
}

function toNvlRel(id: string, from: string, to: string, type: string, weight = 1.0): Relationship {
  const isDashed = ['BELONGS_TO', 'CHILD_OF', 'REFERENCES'].includes(type)
  return {
    id,
    from,
    to,
    captions: [{ value: type }],
    color: EDGE_COLORS[type] ?? '#8C8C8C',
    width: 1 + Math.min(weight, 3),
  }
}