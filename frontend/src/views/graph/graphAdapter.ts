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
  const nodeIds = new Set(vo.nodes.map((n) => n.id))

  return {
    nodes: vo.nodes.map((n) => ({
      id: n.id,
      captions: [{ value: nodeLabel(n.nodeType, {}) }],
      color: getNodeColor(n.nodeType),
      size: getNodeSize(n.nodeType),
    } satisfies Node)),
    relationships: vo.edges
      .filter((e) => nodeIds.has(e.sourceNodeId) && nodeIds.has(e.targetNodeId))
      .map((e, i) => ({
        id: `r-${i}`,
        from: e.sourceNodeId,
        to: e.targetNodeId,
        type: e.edgeType,
        captions: [{ value: shortEdgeLabel(e.edgeType) }],
        color: getEdgeColor(e.edgeType),
        width: 1.5,
      } satisfies Relationship)),
  }
}

export function transformSubgraphResponse(res: SubgraphResponse): NvlGraphData {
  const nodeIds = new Set(res.nodes.map((n) => n.id))

  return {
    nodes: res.nodes.map((n) => ({
      id: n.id,
      captions: [{ value: nodeLabel(n.nodeType, n.properties) }],
      color: getNodeColor(n.nodeType),
      size: getNodeSize(n.nodeType),
    } satisfies Node)),
    relationships: res.edges
      .filter((e) => nodeIds.has(e.sourceNodeId) && nodeIds.has(e.targetNodeId))
      .map((e, i) => ({
        id: `sr-${i}`,
        from: e.sourceNodeId,
        to: e.targetNodeId,
        type: e.edgeType,
        captions: [{ value: shortEdgeLabel(e.edgeType) }],
        color: getEdgeColor(e.edgeType),
        width: 1 + Math.min(e.weight, 3),
      } satisfies Relationship)),
  }
}

function getNodeColor(nodeType: string): string {
  return NODE_COLORS[nodeType] ?? '#8C8C8C'
}

function getNodeSize(nodeType: string): number {
  return NODE_SIZES[nodeType] ?? 28
}

function getEdgeColor(edgeType: string): string {
  return EDGE_COLORS[edgeType] ?? '#8C8C8C'
}

/** 缩短边标签：BELONGS_TO_SUBJECT → B_SUBJECT */
function shortEdgeLabel(type: string): string {
  return type.length > 16 ? type.replace('BELONGS_TO_', 'B_').replace('PREREQUISITE_', 'PRE_') : type
}

function nodeLabel(nodeType: string, props: Record<string, unknown>): string {
  return (props.name as string) ?? (props.label as string) ?? nodeType
}