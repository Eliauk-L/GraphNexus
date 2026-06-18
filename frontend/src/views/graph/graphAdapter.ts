import type { GraphSubgraphVO, SubgraphResponse } from '@/api/types'
import type { Node, Relationship } from '@neo4j-nvl/base'

export interface NvlGraphData {
  nodes: Node[]
  relationships: Relationship[]
}

// Neo4j Bloom 风格颜色
const NODE_COLORS: Record<string, string> = {
  KnowledgePoint: '#4A90D9',
  Student: '#52C41A',
  Entity: '#8C8C8C',
  Exam: '#FAAD14',
  KnowledgeCategory: '#722ED1',
  Document: '#BFBFBF',
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

export function transformGraphSubgraphVO(vo: GraphSubgraphVO): NvlGraphData {
  const relIdCounter = (() => { let i = 0; return () => `r-${i++}` })()

  return {
    nodes: vo.nodes.map((n) => ({
      id: n.id,
      captions: [{ value: nodeLabel(n.nodeType, {}) }],
      color: NODE_COLORS[n.nodeType] ?? '#8C8C8C',
      size: NODE_SIZES[n.nodeType] ?? 28,
    } satisfies Node)),
    relationships: vo.edges.map((e) => ({
      id: relIdCounter(),
      from: e.sourceNodeId,
      to: e.targetNodeId,
      type: e.edgeType,
      captions: [{ value: e.edgeType }],
      color: EDGE_COLORS[e.edgeType] ?? '#8C8C8C',
      width: 1.5,
    } satisfies Relationship)),
  }
}

export function transformSubgraphResponse(res: SubgraphResponse): NvlGraphData {
  const relIdCounter = (() => { let i = 0; return () => `sr-${i++}` })()

  return {
    nodes: res.nodes.map((n) => ({
      id: n.id,
      captions: [{ value: nodeLabel(n.nodeType, n.properties) }],
      color: NODE_COLORS[n.nodeType] ?? '#8C8C8C',
      size: NODE_SIZES[n.nodeType] ?? 28,
      properties: n.properties,
    } satisfies Node & { properties?: Record<string, unknown> })),
    relationships: res.edges.map((e) => ({
      id: relIdCounter(),
      from: e.sourceNodeId,
      to: e.targetNodeId,
      type: e.edgeType,
      captions: [{ value: e.edgeType }],
      color: EDGE_COLORS[e.edgeType] ?? '#8C8C8C',
      width: 1 + Math.min(e.weight, 3),
    } satisfies Relationship)),
  }
}

function nodeLabel(nodeType: string, props: Record<string, unknown>): string {
  return (props.name as string) ?? (props.label as string) ?? nodeType
}